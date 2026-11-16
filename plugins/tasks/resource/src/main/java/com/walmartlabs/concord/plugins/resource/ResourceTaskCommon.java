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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.walmartlabs.concord.common.PathUtils.assertInPath;

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

    public String asString(String path) throws IOException {
        byte[] ab = Files.readAllBytes(normalizePath(path));
        return new String(ab, StandardCharsets.UTF_8);
    }

    public Object asJson(String path) throws IOException {
        return asJson(path, false);
    }

    public Object asJson(String path, boolean eval) throws IOException {
        try (InputStream in = Files.newInputStream(normalizePath(path))) {
            Object result = createObjectMapper().readValue(in, Object.class);
            if (eval) {
                return evaluator.eval(result);
            } else {
                return result;
            }
        }
    }

    public Map<String, Object> asProperties(String path) throws IOException {
        return asProperties(path, false);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> asProperties(String path, boolean eval) throws IOException {
        try (InputStream in = Files.newInputStream(normalizePath(path))) {
            Properties props = new Properties();
            props.load(in);

            HashMap<String, Object> result = new HashMap<>();
            for (final String name : props.stringPropertyNames()) {
                result.put(name, props.getProperty(name));
            }

            if (eval) {
                return (Map<String, Object>) evaluator.eval(result);
            } else {
                return result;
            }
        }
    }

    public Object fromJsonString(String jsonsString) throws IOException {
        return fromJsonString(jsonsString, false);
    }

    public Object fromJsonString(String jsonString, boolean eval) throws IOException {
        Object result = createObjectMapper().readValue(jsonString, Object.class);
        if (eval) {
            return evaluator.eval(result);
        } else {
            return result;
        }
    }

    public Object fromYamlString(String yamlString) throws IOException {
        return fromYamlString(yamlString, false);
    }

    public Object fromYamlString(String yamlString, boolean eval) throws IOException {
        Object result = createObjectMapper(new YAMLFactory()).readValue(yamlString, Object.class);
        if (eval) {
            return evaluator.eval(result);
        } else {
            return result;
        }
    }

    public Object asYaml(String path) throws IOException {
        return asYaml(path, false);
    }

    public Object asYaml(String path, boolean eval) throws IOException {
        try (InputStream in = Files.newInputStream(normalizePath(path))) {
            Object result = createObjectMapper(new YAMLFactory()).readValue(in, Object.class);
            if (eval) {
                return evaluator.eval(result);
            } else {
                return result;
            }
        }
    }

    public String writeAsJson(Object content) throws IOException {
        Path tmpFile = fileService.createTempFile(RESOURCE_PREFIX, JSON_FILE_SUFFIX);
        try (OutputStream out = Files.newOutputStream(tmpFile)) {
            createObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out, content);
        }
        return workDir.relativize(tmpFile.toAbsolutePath()).toString();
    }

    public String writeAsJson(Object content, String path) throws IOException {
        Path dst = assertWorkDirPath(path);
        try (OutputStream out = Files.newOutputStream(dst)) {
            createObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out, content);
        }
        return workDir.relativize(dst.toAbsolutePath()).toString();
    }

    public String writeAsString(String content) throws IOException {
        Path tmpFile = fileService.createTempFile(RESOURCE_PREFIX, TEXT_FILE_SUFFIX);
        Files.write(tmpFile, content.getBytes(StandardCharsets.UTF_8));
        return workDir.relativize(tmpFile.toAbsolutePath()).toString();
    }

    public String writeAsString(String content, String path) throws IOException {
        Path dst = assertWorkDirPath(path);
        Files.write(dst, content.getBytes(StandardCharsets.UTF_8));
        return workDir.relativize(dst.toAbsolutePath()).toString();
    }

    public String writeAsYaml(Object content) throws IOException {
        Path tmpFile = fileService.createTempFile(RESOURCE_PREFIX, YAML_FILE_SUFFIX);
        try (OutputStream out = Files.newOutputStream(tmpFile)) {
            createYamlWriter().writeValue(out, content);
        }
        return workDir.relativize(tmpFile.toAbsolutePath()).toString();
    }

    public String writeAsYaml(Object content, String path) throws IOException {
        Path dst = assertWorkDirPath(path);
        try (OutputStream out = Files.newOutputStream(dst)) {
            createYamlWriter().writeValue(out, content);
        }
        return workDir.relativize(dst.toAbsolutePath()).toString();
    }

    public static String printJson(Object value) throws IOException {
        ObjectMapper mapper = createObjectMapper();

        if (value instanceof String) {
            // parse json string to object
            value = mapper.readValue((String) value, Object.class);
        }

        return mapper.writeValueAsString(value);
    }

    public static String prettyPrintJson(Object value) throws IOException {
        ObjectMapper mapper = createObjectMapper();

        if (value instanceof String) {
            // to add line feeds
            value = mapper.readValue((String) value, Object.class);
        }

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    public static String prettyPrintYaml(Object value) throws IOException {
        return prettyPrintYaml(value, 0);
    }

    public static String prettyPrintYaml(Object value, int indent) throws IOException {
        ObjectMapper mapper = createObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

        if (value instanceof String) {
            value = mapper.readValue((String) value, Object.class);
        }

        String prefix = null;
        if (indent > 0) {
            char[] ch = new char[indent + 1];
            ch[0] = '\n';
            Arrays.fill(ch, 1, ch.length, ' ');
            prefix = new String(ch);
        }

        String s = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        if (prefix != null) {
            s = prefix + s.replace("\n", prefix);
        }
        return s;
    }

    private Path normalizePath(String path) {
        return assertWorkDirPath(path);
    }

    private Path assertWorkDirPath(String path) {
        try {
            return assertInPath(workDir,path);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Not authorized to access file outside of working directory: " + path);
        }
    }

    private static ObjectWriter createYamlWriter() {
        return createObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.SPLIT_LINES))
                .writerWithDefaultPrettyPrinter();
    }

    private static ObjectMapper createObjectMapper() {
        return createObjectMapper(null);
    }

    private static ObjectMapper createObjectMapper(JsonFactory jf) {
        ObjectMapper om = new ObjectMapper(jf);
        om.registerModule(new Jdk8Module());
        om.registerModule(new JavaTimeModule());
        return om;
    }
}
