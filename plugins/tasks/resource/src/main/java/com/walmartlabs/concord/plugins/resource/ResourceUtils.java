package com.walmartlabs.concord.plugins.resource;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ResourceUtils {

    public static final String RESOURCE_PREFIX = "resource_";
    public static final String TEXT_FILE_SUFFIX = ".txt";
    public static final String YAML_FILE_SUFFIX = ".yaml";
    public static final String JSON_FILE_SUFFIX = ".json";

    static String asString(String path) throws IOException {
        byte[] ab = Files.readAllBytes(Paths.get(path));
        return new String(ab);
    }

    static Object asJson(String path) throws IOException {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            return new ObjectMapper().readValue(in, Object.class);
        }
    }

    static Object asYaml(String path) throws IOException {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            return new ObjectMapper(new YAMLFactory()).readValue(in, Object.class);
        }
    }

    static void writeAsJson(Object content, Path tmpFile) throws IOException {
        writeToFile(tmpFile, p -> {
            try (OutputStream out = Files.newOutputStream(p)) {
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out, content);
            }
        });
    }

    static void writeAsString(String content, Path tmpFile) throws IOException {
        writeToFile(tmpFile, p -> {
            Files.write(p, content.getBytes());
        });
    }

    static void writeAsYaml(Object content, Path tmpFile) throws IOException {
        writeToFile(tmpFile, p -> {
            try (OutputStream out = Files.newOutputStream(p)) {
                new ObjectMapper(new YAMLFactory()).writerWithDefaultPrettyPrinter()
                        .writeValue(out, content);
            }
        });
    }

    static String prettyPrintJson(Object json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        if (json instanceof String) {
            // To add line feeds
            json = mapper.readValue((String) json, Object.class);
        }

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
    }

    static void writeToFile(Path file, PathHander h) throws IOException {
        h.handle(file);
    }

    private interface PathHander {

        void handle(Path path) throws IOException;
    }

}
