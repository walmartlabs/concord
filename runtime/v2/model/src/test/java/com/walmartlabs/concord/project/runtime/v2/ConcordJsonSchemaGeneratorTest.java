package com.walmartlabs.concord.project.runtime.v2;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.walmartlabs.concord.runtime.v2.ConcordJsonSchemaGenerator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class ConcordJsonSchemaGeneratorTest {

    @Test
    public void validateOk() throws Exception {
        JsonNode concordYml = new ObjectMapper(new YAMLFactory())
                .readTree(ConcordJsonSchemaGeneratorTest.class.getResourceAsStream("/schema/concord.yml"));

        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonSchema schema = schemaFactory.getSchema(ConcordJsonSchemaGenerator.generate());

        Set<ValidationMessage> validationResult = schema.validate(concordYml);

        System.out.println(validationResult);
        assertTrue(validationResult.isEmpty());
    }
}
