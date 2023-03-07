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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.agent.cfg.AgentConfiguration;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.sdk.Constants;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class PolicyEngineLoader {

    public static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private static final ObjectMapper objectMapper = createObjectMapper();

    private final PolicyEngineRules defaultRules;

    @Inject
    public PolicyEngineLoader(AgentConfiguration cfg) {
        this.defaultRules = readRules(cfg.getDefaultPolicy());
    }

    public PolicyEngine load(Path payloadDir) {
        Path policyFile = payloadDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(Constants.Files.POLICY_FILE_NAME);

        PolicyEngineRules processRules = readRules(policyFile);
        PolicyEngineRules rules = mergeRules(defaultRules, processRules);
        if (rules == null) {
            return null;
        }
        return new PolicyEngine(rules);
    }

    private PolicyEngineRules mergeRules(PolicyEngineRules defaultRules, PolicyEngineRules processRules) {
        if (defaultRules == null && processRules == null) {
            return null;
        }

        Map<String, Object> defaultMap = toMap(defaultRules);
        Map<String, Object> processMap = toMap(processRules);
        Map<String, Object> merged = ConfigurationUtils.deepMerge(defaultMap, processMap);
        return objectMapper.convertValue(merged, PolicyEngineRules.class);
    }

    private static PolicyEngineRules readRules(Path policyFile) {
        if (policyFile == null || Files.notExists(policyFile)) {
            return null;
        };

        try {
            return objectMapper.readValue(policyFile.toFile(), PolicyEngineRules.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> toMap(PolicyEngineRules rules) {
        if (rules == null) {
            return Collections.emptyMap();
        }

        return objectMapper.convertValue(rules, MAP_TYPE);
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new GuavaModule());
        om.registerModule(new Jdk8Module());
        return om;
    }
}
