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

public class ResourceTaskCommon {

    private static final String RESOURCE_PREFIX = "resource_";
    private static final String TEXT_FILE_SUFFIX = ".txt";
    private static final String YAML_FILE_SUFFIX = ".yaml";
    private static final String JSON_FILE_SUFFIX = ".json";

    private final Path workDir;
    private final FileService fileService;
    private final Evaluator evaluator;

    public ResourceTaskCommon(Path workDir, FileService fileService, Evaluator evaluator) {
        this.workDir = workDir;
        this.fileService = fileService;
        this.evaluator = evaluator;
    }

    public static String asString(String path) throws IOException {
        byte[] ab = Files.readAllBytes(Paths.get(path));
        return new String(ab);
    }

    public Object asJson(String path) throws IOException {
        return asJson(path, false);
    }

    public Object asJson(String path, boolean eval) throws IOException {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            Object result = new ObjectMapper().readValue(in, Object.class);
            if (eval) {
                return evaluator.eval(result);
            } else {
                return result;
            }
        }
    }

    public Object asYaml(String path) throws IOException {
        return asYaml(path, false);
    }

    public Object asYaml(String path, boolean eval) throws IOException {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            Object result = new ObjectMapper(new YAMLFactory()).readValue(in, Object.class);
            if (eval) {
                return evaluator.eval(result);
            } else {
                return result;
            }
        }
    }

    public String writeAsJson(Object content) throws IOException {
        Path tmpFile = fileService.createTempFile(RESOURCE_PREFIX, JSON_FILE_SUFFIX);
        writeToFile(tmpFile, p -> {
            try (OutputStream out = Files.newOutputStream(p)) {
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out, content);
            }
        });
        return workDir.relativize(tmpFile.toAbsolutePath()).toString();
    }

    public String writeAsString(String content) throws IOException {
        Path tmpFile = fileService.createTempFile(RESOURCE_PREFIX, TEXT_FILE_SUFFIX);
        writeToFile(tmpFile, p -> Files.write(p, content.getBytes()));
        return workDir.relativize(tmpFile.toAbsolutePath()).toString();
    }

    public String writeAsYaml(Object content) throws IOException {
        Path tmpFile = fileService.createTempFile(RESOURCE_PREFIX, YAML_FILE_SUFFIX);
        writeToFile(tmpFile, p -> {
            try (OutputStream out = Files.newOutputStream(p)) {
                new ObjectMapper(new YAMLFactory()).writerWithDefaultPrettyPrinter()
                        .writeValue(out, content);
            }
        });
        return workDir.relativize(tmpFile.toAbsolutePath()).toString();
    }

    public static String prettyPrintJson(Object json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        if (json instanceof String) {
            // To add line feeds
            json = mapper.readValue((String) json, Object.class);
        }

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
    }

    static void writeToFile(Path file, PathHandler h) throws IOException {
        h.handle(file);
    }

    private interface PathHandler {

        void handle(Path path) throws IOException;
    }
}
