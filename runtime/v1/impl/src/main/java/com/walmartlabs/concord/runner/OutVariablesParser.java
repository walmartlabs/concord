package com.walmartlabs.concord.runner;

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
import com.walmartlabs.concord.project.InternalConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;

public class OutVariablesParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void write(Path storeDir, Map<String, Object> vars) {
        try {
            if (!Files.exists(storeDir)) {
                Files.createDirectories(storeDir);
            }

            Path p = storeDir.resolve(InternalConstants.Files.OUT_VALUES_FILE_NAME);
            try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                objectMapper.writeValue(out, vars);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while saving OUT variables", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(Path storeDir) {
        Path p = storeDir.resolve(InternalConstants.Files.OUT_VALUES_FILE_NAME);
        if (!Files.exists(p)) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(p.toFile(), Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Error while reading OUT variables", e);
        }
    }
}
