package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;

@Named
@Singleton
public class ProjectDefinitionProcessor implements PayloadProcessor {

    private final ProjectLoader loader = new ProjectLoader();

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        if (workspace == null) {
            return chain.process(payload);
        }

        try {
            ProjectDefinition pd = loader.load(workspace);
            payload = payload.putHeader(Payload.PROJECT_DEFINITION, pd);
            return chain.process(payload);
        } catch (IOException e) {
            throw new ProcessException("Error while loading a project file: " + workspace, e);
        }
    }
}
