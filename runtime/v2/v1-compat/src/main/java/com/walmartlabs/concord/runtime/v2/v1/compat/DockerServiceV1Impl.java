package com.walmartlabs.concord.runtime.v2.v1.compat;

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

import com.walmartlabs.concord.common.DockerProcessBuilder;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.DockerContainerSpec;
import com.walmartlabs.concord.sdk.DockerService;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

public class DockerServiceV1Impl implements DockerService {

    private static final String WORKSPACE_TARGET_DIR = "/workspace";

    private final com.walmartlabs.concord.runtime.v2.sdk.DockerService delegate;

    private final List<String> extraVolumes;

    @Inject
    public DockerServiceV1Impl(com.walmartlabs.concord.runtime.v2.sdk.DockerService delegate, RunnerConfiguration runnerCfg) {
        this.delegate = delegate;
        this.extraVolumes = runnerCfg.docker().extraVolumes();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Process start(Context ctx, DockerContainerSpec spec) throws IOException {
        DockerProcessBuilder b = DockerProcessBuilder.from(ctx, spec);

        b.env(createEffectiveEnv(spec.env()));

        List<String> volumes = new ArrayList<>();
        // add the default volume - mount the process' workDir as /workspace
        volumes.add("${" + Constants.Context.WORK_DIR_KEY + "}:" + WORKSPACE_TARGET_DIR);
        // add extra volumes from the runner's arguments
        volumes.addAll(extraVolumes);

        b.volumes((Collection<String>) ctx.interpolate(volumes));

        return b.build();
    }

    @Override
    public int start(Context ctx, DockerContainerSpec spec, LogCallback outCallback, LogCallback errCallback) throws IOException, InterruptedException {
        // TODO sdk-refactor
        //return delegate.start(spec, outCallback::onLog, errCallback::onLog);
        return 0;
    }

    private static Map<String, String> createEffectiveEnv(Map<String, String> env) {
        Map<String, String> m = new HashMap<>();

        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost == null) {
            dockerHost = "unix:///var/run/docker.sock";
        }
        m.put("DOCKER_HOST", dockerHost);

        if (env != null) {
            m.putAll(env);
        }

        return m;
    }

//    private static com.walmartlabs.concord.runtime.v2.sdk.DockerContainerSpec toV2Spec(DockerContainerSpec spec) {
//        return com.walmartlabs.concord.runtime.v2.sdk.DockerContainerSpec.builder()
//                .args(spec.args())
//                .cpu(spec.cpu())
//                .debug(spec.debug())
//                .entryPoint(spec.entryPoint())
//                .env(spec.env())
//                .envFile(spec.envFile())
//                .forcePull(spec.forcePull())
//                .image(spec.image())
//                .labels(spec.labels())
//                .memory(spec.memory())
//                .options(spec.options())
//                .
//                .build();
//    }
}
