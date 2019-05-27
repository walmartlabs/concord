package com.walmartlabs.concord.project.yaml;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.project.yaml.model.*;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class YamlParser {

    private final ObjectMapper objectMapper;

    public YamlParser() {
        ObjectMapper om = new ObjectMapper(new YAMLFactory()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));

        SimpleModule module = new SimpleModule();
        module.addDeserializer(YamlStep.class, YamlDeserializers.getYamlStepDeserializer());
        module.addDeserializer(YamlFormField.class, YamlDeserializers.getYamlFormFieldDeserializer());
        module.addDeserializer(YamlDefinitionFile.class, YamlDeserializers.getYamlDefinitionFileDeserializer());
        module.addDeserializer(YamlTrigger.class, YamlDeserializers.getYamlTriggerDeserializer());
        module.addDeserializer(YamlImport.class, YamlDeserializers.getYamlImportDeserializer());

        om.registerModule(module);
        om.registerModule(new GuavaModule());
        om.registerModule(new Jdk8Module());
        this.objectMapper = om;
    }

    public YamlProject parseProject(Path baseDir, Path file) throws YamlParserException {
        try {
            return objectMapper.readValue(file.toFile(), YamlProject.class);
        } catch (IOException e) {
            if (e instanceof JsonProcessingException) {
                JsonProcessingException jpe = (JsonProcessingException) e;
                throw toErr("(" + baseDir.relativize(file) + "): Error", jpe);
            }
            throw new YamlParserException("Error while loading a project file: " + baseDir.relativize(file), e);
        }
    }

    public YamlDefinitionFile parseDefinitionFile(Path baseDir, Path path) throws YamlParserException {
        try {
            return objectMapper.readValue(path.toFile(), YamlDefinitionFile.class);
        } catch (IOException e) {
            if (e instanceof JsonProcessingException) {
                JsonProcessingException jpe = (JsonProcessingException) e;
                throw toErr("Error while loading flow definitions: " + baseDir.relativize(path), jpe);
            }
            throw new YamlParserException("Error while loading flow definitions: " + baseDir.relativize(path), e);
        }
    }

    public YamlDefinitionFile parseDefinitionFile(InputStream in) throws YamlParserException {
        try {
            return objectMapper.readValue(in, YamlDefinitionFile.class);
        } catch (IOException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MarkedYAMLException) {
                throw new YamlParserException("Error while loading a definition file: " + e.getMessage());
            }
            throw new YamlParserException("Error while loading a definition file", e);
        }
    }

    public YamlProfileFile parseProfileFile(Path baseDir, Path path) throws YamlParserException {
        try {
            return objectMapper.readValue(path.toFile(), YamlProfileFile.class);
        } catch (IOException e) {
            if (e instanceof MismatchedInputException) {
                throw new YamlParserException("Error while loading profiles: " + baseDir.relativize(path) + ". " + e.getMessage());
            }
            throw new YamlParserException("Error while loading profiles: " + baseDir.relativize(path), e);
        }
    }

    private static YamlParserException toErr(String msg, JsonProcessingException jpe) {
        JsonLocation loc = jpe.getLocation();
        String originalMsg = jpe.getOriginalMessage();
        return new YamlParserException(msg + " @ " + loc + ". " + originalMsg);
    }
}
