package com.walmartlabs.concord.agent.executors.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.agent.ExecutionException;
import com.walmartlabs.concord.agent.JobRequest;
import com.walmartlabs.concord.agent.logging.ProcessLogFactory;
import com.walmartlabs.concord.agent.logging.RedirectedProcessLog;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class RunnerJob {

    @SuppressWarnings("unchecked")
    public static RunnerJob from(JobRequest jobRequest, ProcessLogFactory processLogFactory) throws ExecutionException {
        Map<String, Object> cfg = Collections.emptyMap();

        Path payloadDir = jobRequest.getPayloadDir();

        Path p = payloadDir.resolve(InternalConstants.Files.REQUEST_DATA_FILE_NAME);
        if (Files.exists(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                cfg = new ObjectMapper().readValue(in, Map.class);
            } catch (IOException e) {
                throw new ExecutionException("Error while reading process configuration", e);
            }
        }

        RedirectedProcessLog log = processLogFactory.createRedirectedLog(jobRequest.getInstanceId());
        return new RunnerJob(jobRequest.getInstanceId(), payloadDir, cfg, log);
    }

    private final UUID instanceId;
    private final Path payloadDir;
    private final Map<String, Object> cfg;
    private final boolean debugMode;
    private final RedirectedProcessLog log;

    private RunnerJob(UUID instanceId, Path payloadDir, Map<String, Object> cfg, RedirectedProcessLog log) {
        this.instanceId = instanceId;
        this.payloadDir = payloadDir;
        this.cfg = cfg;
        this.debugMode = debugMode(this);
        this.log = log;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public Path getPayloadDir() {
        return payloadDir;
    }

    public Map<String, Object> getCfg() {
        return cfg;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public RedirectedProcessLog getLog() {
        return log;
    }

    private static boolean debugMode(RunnerJob job) {
        Object v = job.cfg.get(Constants.Request.DEBUG_KEY);
        if (v instanceof String) {
            // allows `curl ... -F debug=true`
            return Boolean.parseBoolean((String) v);
        }

        return Boolean.TRUE.equals(v);
    }

    @Override
    public String toString() {
        return "RunnerJob{" +
                "instanceId=" + instanceId +
                ", debugMode=" + debugMode +
                '}';
    }
}
