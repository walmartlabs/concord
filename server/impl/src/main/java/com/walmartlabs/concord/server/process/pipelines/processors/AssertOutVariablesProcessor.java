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

import com.walmartlabs.concord.server.jooq.enums.OutVariablesMode;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.user.UserManager;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import java.util.Set;
import java.util.UUID;

public class AssertOutVariablesProcessor implements PayloadProcessor {

    private final ProjectDao projectDao;
    private final ProjectAccessManager projectAccessManager;
    private final UserManager userManager;

    @Inject
    public AssertOutVariablesProcessor(ProjectDao projectDao,
                                       ProjectAccessManager projectAccessManager,
                                       UserManager userManager) {

        this.projectDao = projectDao;
        this.projectAccessManager = projectAccessManager;
        this.userManager = userManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        Set<String> outVars = payload.getHeader(Payload.OUT_EXPRESSIONS);

        if (outVars == null || outVars.isEmpty()) {
            return chain.process(payload);
        }

        assertOutVariables(payload);

        return chain.process(payload);
    }

    private void assertOutVariables(Payload payload) {
        if (!isOutVariablesAllowed(payload)) {
            throw new ProcessException(payload.getProcessKey(),
                    "The project is not accepting custom out variables: " + payload.getHeader(Payload.PROJECT_ID),
                    Status.BAD_REQUEST);
        }
    }

    private boolean isOutVariablesAllowed(Payload payload) {
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return true;
        }

        ProjectEntry p = projectDao.get(projectId);
        if (p == null) {
            throw new ProcessException(payload.getProcessKey(), "Project not found: " + projectId);
        }

        OutVariablesMode m = p.getOutVariablesMode();
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
                throw new IllegalArgumentException("Unsupported out variables mode: " + m);
        }
    }
}
