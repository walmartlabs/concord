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

import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Inject;

/**
 * Validates that the IDs restored from the initial state still reference
 * existing entities. This catches cases where a project or repository
 * was deleted/recreated between the original run and a restart.
 */
public class RestoredPayloadValidationProcessor implements PayloadProcessor {

    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;

    @Inject
    public RestoredPayloadValidationProcessor(ProjectDao projectDao,
                                              RepositoryDao repositoryDao) {
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        var processKey = payload.getProcessKey();

        var projectId = payload.getHeader(Payload.PROJECT_ID);
        var repoId = payload.getHeader(Payload.REPOSITORY_ID);

        if (projectId != null) {
            var project = projectDao.get(projectId);
            if (project == null) {
                throw new ProcessException(processKey,
                        "Project not found: " + projectId + ". It may have been deleted or recreated since the process was originally started.");
            }
        }

        if (projectId != null && repoId != null) {
            var repo = repositoryDao.get(projectId, repoId);
            if (repo == null) {
                throw new ProcessException(processKey,
                        "Repository not found: " + repoId + " in project " + projectId
                                + ". It may have been deleted or recreated since the process was originally started.");
            }
        }

        return chain.process(payload);
    }
}
