package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.process.Payload;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class AuthorizationProcessor implements PayloadProcessor {

    private final ProjectAccessManager accessManager;

    @Inject
    public AuthorizationProcessor(ProjectAccessManager accessManager) {
        this.accessManager = accessManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return chain.process(payload);
        }

        accessManager.assertProjectAccess(projectId, ResourceAccessLevel.READER, false);
        return chain.process(payload);
    }
}
