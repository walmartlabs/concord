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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.jooq.enums.RawPayloadMode;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.user.UserManager;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AssertWorkspaceArchiveProcessor implements PayloadProcessor {

    private static final Set<String> PROJECT_ROOT_FILE_NAMES = new HashSet<>(Arrays.asList(Constants.Files.PROJECT_ROOT_FILE_NAMES));

    private final ProjectDao projectDao;
    private final ProjectAccessManager projectAccessManager;
    private final UserManager userManager;

    @Inject
    public AssertWorkspaceArchiveProcessor(ProjectDao projectDao,
                                           ProjectAccessManager projectAccessManager,
                                           UserManager userManager) {

        this.projectDao = projectDao;
        this.projectAccessManager = projectAccessManager;
        this.userManager = userManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        if (!hasRawPayloadAttachment(payload)) {
            return chain.process(payload);
        }

        assertAcceptsRawPayload(payload);

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

    /**
     * @return {@code true} if the payload contains any file we consider
     * a "raw payload" attachment: workspace archives, concord.yml, etc.
     */
    private boolean hasRawPayloadAttachment(Payload payload) {
        if (payload.getAttachment(Payload.WORKSPACE_ARCHIVE) != null) {
            return true;
        }

        return payload.getAttachments().keySet().stream()
                .anyMatch(k -> PROJECT_ROOT_FILE_NAMES.contains(k) ||
                        (k.startsWith(Constants.Files.PROJECT_FILES_DIR_NAME + "/") && k.endsWith(".yml")));
    }
}
