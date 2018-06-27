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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.project.yaml.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class YamlParser {

    private final ObjectMapper objectMapper;

    public YamlParser() {
        ObjectMapper om = new ObjectMapper(new YAMLFactory());

        SimpleModule module = new SimpleModule();
        module.addDeserializer(YamlStep.class, YamlDeserializers.getYamlStepDeserializer());
        module.addDeserializer(YamlFormField.class, YamlDeserializers.getYamlFormFieldDeserializer());
        module.addDeserializer(YamlDefinitionFile.class, YamlDeserializers.getYamlDefinitionFileDeserializer());

        om.registerModule(module);

        this.objectMapper = om;
    }

    public YamlProject parseProject(Path path) throws IOException {
        try {
            return objectMapper.readValue(path.toFile(), YamlProject.class);
        } catch (IOException e) {
            throw new IOException("Error while loading a project file: " + path, e);
        }
    }

    public YamlDefinitionFile parseDefinitionFile(Path path) throws IOException {
        try {
            return objectMapper.readValue(path.toFile(), YamlDefinitionFile.class);
        } catch (IOException e) {
            throw new IOException("Error while loading flow definitions: " + path, e);
        }
    }

    public YamlDefinitionFile parseDefinitionFile(InputStream in) throws IOException {
        return objectMapper.readValue(in, YamlDefinitionFile.class);
    }

    public YamlProfileFile parseProfileFile(Path path) throws IOException {
        try {
            return objectMapper.readValue(path.toFile(), YamlProfileFile.class);
        } catch (IOException e) {
            throw new IOException("Error while loading profiles: " + path, e);
        }
    }
}
