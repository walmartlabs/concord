package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Named
@Singleton
public class ProjectDefinitionProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProjectDefinitionProcessor.class);

    private final ProjectLoader loader = new ProjectLoader();

    private final LogManager logManager;

    @Inject
    public ProjectDefinitionProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        if (workspace == null) {
            return chain.process(payload);
        }

        try {
            ProjectDefinition pd = loader.load(workspace);
            payload = payload.putHeader(Payload.PROJECT_DEFINITION, pd);
            return chain.process(payload);
        } catch (IOException e) {
            log.warn("process ['{}'] -> project loading error: {}", instanceId, workspace, e);
            logManager.error(instanceId,"Error while loading a project file: " + workspace, e);
            throw new ProcessException(instanceId, "Error while loading a project file: " + workspace, e);
        }
    }
}
