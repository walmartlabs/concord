package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.yaml.YamlParser;
import com.walmartlabs.concord.project.yaml.YamlProjectConverter;
import com.walmartlabs.concord.project.yaml.model.YamlProject;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Named
@Singleton
public class ProjectDefinitionProcessor implements PayloadProcessor {

    private final YamlParser parser;

    public ProjectDefinitionProcessor() {
        this.parser = new YamlParser();
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        if (workspace == null) {
            return chain.process(payload);
        }

        Path projectFile = workspace.resolve(Constants.PROJECT_FILE_NAME);
        if (!Files.exists(projectFile)) {
            return chain.process(payload);
        }

        try {
            YamlProject yp = parser.parseProject(projectFile);
            ProjectDefinition pd = YamlProjectConverter.convert(yp);
            payload = payload.putHeader(Payload.PROJECT_DEFINITION, pd);

            return chain.process(payload);
        } catch (IOException e) {
            throw new ProcessException("Error while loading a project file: " + projectFile, e);
        }
    }
}
