package com.walmartlabs.concord.runtime.model;

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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.imports.Import;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ImportTest {

    @Test
    public void testSerialization() throws Exception {
        Import item = Import.GitDefinition.builder()
                .url("http://")
                .dest("/tmp")
                .path("/path")
                .version("1.0.0")
                .secret(Import.SecretDefinition.builder()
                        .org("default")
                        .name("secret-name")
                        .password("secret-password")
                        .build())
                .build();

        ObjectMapper om = new ObjectMapper();
        om.enable(SerializationFeature.INDENT_OUTPUT);
        om.registerModule(new GuavaModule());
        om.registerModule(new Jdk8Module());

        String r = om.writeValueAsString(item);

        Import di = om.readValue(r, Import.class);
        assertEquals(item, di);
    }
}
