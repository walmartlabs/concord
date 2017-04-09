package com.walmartlabs.concord.project.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.project.yaml.model.YamlDefinitionFile;
import com.walmartlabs.concord.project.yaml.model.YamlFormField;
import com.walmartlabs.concord.project.yaml.model.YamlProject;
import com.walmartlabs.concord.project.yaml.model.YamlStep;

import java.io.IOException;
import java.net.URL;
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
        return objectMapper.readValue(path.toFile(), YamlProject.class);
    }

    public YamlDefinitionFile parseDefinitionFile(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), YamlDefinitionFile.class);
    }
}
