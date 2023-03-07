package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * A {@link JobRequest} plus the process configuration loaded from the request's
 * payload directory.
 */
public class ConfiguredJobRequest extends JobRequest {

    @SuppressWarnings("unchecked")
    public static ConfiguredJobRequest from(JobRequest req, PolicyEngineLoader policyEngineLoader) throws ExecutionException {
        Map<String, Object> cfg = Collections.emptyMap();

        Path p = req.getPayloadDir().resolve(Constants.Files.CONFIGURATION_FILE_NAME);
        if (Files.exists(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                cfg = new ObjectMapper().readValue(in, Map.class);
            } catch (IOException e) {
                throw new ExecutionException("Error while reading process configuration", e);
            }
        }

        return new ConfiguredJobRequest(req, cfg, policyEngineLoader.load(req.getPayloadDir()));
    }

    private final Map<String, Object> processCfg;
    private final PolicyEngine policyEngine;

    private ConfiguredJobRequest(JobRequest src, Map<String, Object> processCfg, PolicyEngine policyEngine) {
        super(src);
        this.processCfg = processCfg;
        this.policyEngine = policyEngine;
    }

    public Map<String, Object> getProcessCfg() {
        return processCfg;
    }

    public PolicyEngine getPolicyEngine() {
        return policyEngine;
    }
}
