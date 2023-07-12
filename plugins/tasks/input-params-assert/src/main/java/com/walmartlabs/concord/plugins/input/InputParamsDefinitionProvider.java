package com.walmartlabs.concord.plugins.input;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.runner.PersistenceService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.Map;

@Singleton
public class InputParamsDefinitionProvider {

    public static final String DEFAULT_SCHEMA_FILENAME = "concord-flow-call-schema.json";

    private static final TypeReference<Map<String, JsonNode>> MAP_TYPE = new TypeReference<Map<String, JsonNode>>() {
    };
    private static final ObjectMapper mapper = new ObjectMapper();

    private Map<String, JsonNode> definitions;

    private final PersistenceService persistenceService;

    @Inject
    public InputParamsDefinitionProvider(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public synchronized JsonNode get(String flowName) {
        if (definitions == null) {
            definitions = loadDefinitions();
        }

        return definitions.get(flowName);
    }

    private Map<String, JsonNode> loadDefinitions() {
        Map<String, JsonNode> result = persistenceService.loadPersistedFile(DEFAULT_SCHEMA_FILENAME, inputStream -> mapper.readValue(inputStream, MAP_TYPE));
        if (result == null) {
            return Collections.emptyMap();
        }
        return result;
    }
}
