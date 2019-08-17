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

import com.walmartlabs.concord.server.jooq.enums.RawPayloadMode;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.user.UserManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Named
public class AssertWorkspaceArchiveProcessor implements PayloadProcessor {

    private final LogManager logManager;
    private final ProjectDao projectDao;
    private final ProjectAccessManager projectAccessManager;
    private final UserManager userManager;

    @Inject
    public AssertWorkspaceArchiveProcessor(LogManager logManager,
                                           ProjectDao projectDao,
                                           ProjectAccessManager projectAccessManager,
                                           UserManager userManager) {

        this.logManager = logManager;
        this.projectDao = projectDao;
        this.projectAccessManager = projectAccessManager;
        this.userManager = userManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        Path archive = payload.getAttachment(Payload.WORKSPACE_ARCHIVE);
        if (archive == null) {
            return chain.process(payload);
        }

        assertAcceptsRawPayload(payload);

        if (!Files.exists(archive)) {
            logManager.error(processKey, "No input archive found: " + archive);
            throw new ProcessException(processKey, "No input archive found: " + archive, Status.BAD_REQUEST);
        }

        return chain.process(payload);
    }

    private void assertAcceptsRawPayload(Payload payload) {
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);

        if (!isRawPayloadAllowed(payload)) {
            throw new ProcessException(payload.getProcessKey(), "The project is not accepting raw payloads: " + projectId,
                    Status.BAD_REQUEST);
        }
    }

    private boolean isRawPayloadAllowed(Payload payload) {
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return true;
        }

        ProjectEntry p = projectDao.get(projectId);
        if (p == null) {
            throw new ProcessException(payload.getProcessKey(), "Project not found: " + projectId);
        }

        RawPayloadMode m = p.getRawPayloadMode();
        switch (m) {
            case DISABLED: {
                return false;
            }
            case OWNERS: {
                return projectAccessManager.hasAccess(p, ResourceAccessLevel.OWNER, false);
            }
            case TEAM_MEMBERS: {
                return projectAccessManager.isTeamMember(p.getId());
            }
            case ORG_MEMBERS: {
                return userManager.isInOrganization(p.getOrgId());
            }
            case EVERYONE: {
                return true;
            }
            default:
                throw new IllegalArgumentException("Unsupported raw payload mode: " + m);
        }
    }
}
