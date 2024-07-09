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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;

public class FlowCallInputParamsAssert {

    private static final Logger log = LoggerFactory.getLogger(FlowCallInputParamsAssert.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    private final InputParamsDefinitionProvider definitionProvider;

    private final boolean debug;

    @Inject
    public FlowCallInputParamsAssert(InputParamsDefinitionProvider definitionProvider, ProcessConfiguration cfg) {
        this.definitionProvider = definitionProvider;
        this.debug = cfg.debug();
    }

    public void process(String flowName, Map<String, Object> input) {
        JsonNode definition = definitionProvider.get(flowName);
        if (definition == null) {
            if (debug) {
                log.info("Can't find input params definition for '{}' flow", flowName);
            }
            return;
        }

        if (debug) {
            log.info("Validating input params for '{}' flow", flowName);
        }

        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
        JsonSchema schema = schemaFactory.getSchema(definition);

        Set<ValidationMessage> validationResult = schema.validate(mapper.valueToTree(input));
        if (!validationResult.isEmpty()) {
            throw new UserDefinedException("Input params validation failed: " + validationResult);
        }
    }
}
