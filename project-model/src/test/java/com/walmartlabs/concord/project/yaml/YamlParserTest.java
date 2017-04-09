package com.walmartlabs.concord.project.yaml;

import com.walmartlabs.concord.project.yaml.model.YamlDefinitionFile;
import com.walmartlabs.concord.project.yaml.model.YamlProject;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class YamlParserTest {

    @Test
    public void testDefinitionFile() throws Exception {
        Path resource = Paths.get(ClassLoader.getSystemResource("def.yml").toURI());

        YamlParser p = new YamlParser();
        YamlDefinitionFile df = p.parseDefinitionFile(resource);
        System.out.println(df);
    }
}
