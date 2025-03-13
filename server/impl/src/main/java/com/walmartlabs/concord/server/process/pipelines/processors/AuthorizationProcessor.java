package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;

import static java.util.Objects.requireNonNull;

@Named
public class AuthorizationProcessor implements PayloadProcessor {

    private final ProjectDao projectDao;
    private final ProjectAccessManager accessManager;

    @Inject
    public AuthorizationProcessor(ProjectDao projectDao, ProjectAccessManager accessManager) {
        this.projectDao = requireNonNull(projectDao);
        this.accessManager = requireNonNull(accessManager);
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        var projectId = payload.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return chain.process(payload);
        }

        var projectEntry = projectDao.get(projectId);
        if (projectEntry == null) {
            throw new ValidationErrorsException("Project not found: " + projectId);
        }

        switch (projectEntry.getProcessExecMode()) {
            case DISABLED -> throw new ConcordApplicationException("Process execution is disabled for the project.");
            case READERS -> accessManager.assertAccess(projectId, ResourceAccessLevel.READER, false);
            case WRITERS -> accessManager.assertAccess(projectId, ResourceAccessLevel.WRITER, false);
        }

        return chain.process(payload);
    }
}
