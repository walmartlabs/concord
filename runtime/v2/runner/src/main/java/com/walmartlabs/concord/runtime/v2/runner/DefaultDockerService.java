package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.common.TruncBufferedReader;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.sdk.DockerContainerSpec;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.walmartlabs.concord.runtime.v2.runner.DockerProcessBuilder.DockerProcess;

public class DefaultDockerService implements DockerService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDockerService.class);

    private static final int SUCCESS_EXIT_CODE = 0;
    private static final String WORKSPACE_TARGET_DIR = "/workspace";

    private static final Pattern[] REGISTRY_ERROR_PATTERNS = {
            Pattern.compile("Error response from daemon.*received unexpected HTTP status: 5.*"),
            Pattern.compile("Error response from daemon.*Get.*connection refused.*"),
            Pattern.compile("Error response from daemon.*Client.Timeout exceeded.*")
    };

    private final WorkingDirectory workingDirectory;
    private final InstanceId instanceId;
    private final List<String> extraVolumes;
    private final boolean exposeDockerDaemon;

    @Inject
    public DefaultDockerService(WorkingDirectory workingDirectory, InstanceId instanceId, RunnerConfiguration runnerCfg) {
        this.workingDirectory = workingDirectory;
        this.instanceId = instanceId;
        this.extraVolumes = runnerCfg.docker().extraVolumes();
        this.exposeDockerDaemon = runnerCfg.docker().exposeDockerDaemon();
    }

    @Override
    public int start(DockerContainerSpec spec, LogCallback outCallback, LogCallback errCallback) throws IOException, InterruptedException {
        int tryCount = 0;
        int result;
        int retryCount = Math.max(spec.pullRetryCount(), 0);
        long retryInterval = spec.pullRetryInterval();

        do {
            try (DockerProcess dp = build(spec)) {
                Process p = dp.start();

                LogCapture c = new LogCapture(outCallback);
                streamToLog(p.getInputStream(), c);
                if (errCallback != null) {
                    streamToLog(p.getErrorStream(), errCallback);
                }

                result = p.waitFor();
                if (result == SUCCESS_EXIT_CODE || retryCount == 0 || tryCount >= retryCount) {
                    return result;
                }

                if (!needRetry(c.getLines())) {
                    return result;
                }

                log.info("Error pulling the image. Retry after {} sec", retryInterval / 1000);
                sleep(retryInterval);
                tryCount++;
            }
        } while (!Thread.currentThread().isInterrupted() && tryCount <= retryCount);

        return result;
    }

    private DockerProcess build(DockerContainerSpec spec) throws IOException {
        DockerProcessBuilder b = DockerProcessBuilder.from(instanceId.getValue(), spec);

        b.env(createEffectiveEnv(spec.env(), exposeDockerDaemon));
        // add the default volume - mount the process' workDir as /workspace
        b.volume(workingDirectory.getValue().toString(), WORKSPACE_TARGET_DIR);
        // add extra volumes from the runner's arguments
        extraVolumes.forEach(b::volume);

        return b.build();
    }

    private static boolean needRetry(List<String> lines) {
        for (String l : lines) {
            for (Pattern p : REGISTRY_ERROR_PATTERNS) {
                if (p.matcher(l).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void streamToLog(InputStream in, LogCallback callback) throws IOException {
        BufferedReader reader = new TruncBufferedReader(new InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null) {
            callback.onLog(line);
        }
    }

    private static Map<String, String> createEffectiveEnv(Map<String, String> env, boolean exposeDockerDaemon) {
        Map<String, String> m = new HashMap<>();

        if (exposeDockerDaemon) {
            String dockerHost = System.getenv("DOCKER_HOST");
            if (dockerHost == null) {
                dockerHost = "unix:///var/run/docker.sock";
            }
            m.put("DOCKER_HOST", dockerHost);
        }

        if (env != null) {
            m.putAll(env);
        }

        return m;
    }

    private static class LogCapture implements LogCallback {

        private static final int MAX_CAPTURE_LINES = 5;

        private final LogCallback delegate;
        private final List<String> lines;

        private LogCapture(LogCallback delegate) {
            this.delegate = delegate;
            this.lines = new ArrayList<>();
        }

        @Override
        public void onLog(String line) {
            if (delegate != null) {
                delegate.onLog(line);
            }

            if (lines.size() <= MAX_CAPTURE_LINES) {
                lines.add(line);
            }
        }

        List<String> getLines() {
            return lines;
        }
    }
}
