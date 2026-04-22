package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class ProcessAccessManager {

    private final ProjectAccessManager projectAccessManager;
    private final ProcessQueueManager processQueueManager;

    @Inject
    public ProcessAccessManager(ProjectAccessManager projectAccessManager,
                                ProcessQueueManager processQueueManager) {

        this.projectAccessManager = projectAccessManager;
        this.processQueueManager = processQueueManager;
    }

    public ProcessEntry assertAccess(UUID instanceId, Set<ProcessDataInclude> includes) {
        var entry = processQueueManager.get(PartialProcessKey.from(instanceId), includes);
        if (entry == null) {
            throw new ConcordApplicationException("Process instance not found: " + instanceId, Status.NOT_FOUND);
        }

        if (Roles.isAdmin() || Roles.isGlobalReader()) {
            return entry;
        }

        var current = UserPrincipal.assertCurrent();
        if (current.getId().equals(entry.initiatorId())) {
            return entry;
        }

        var sessionKey = SessionKeyPrincipal.getCurrent();
        if (sessionKey != null) {
            var processKey = new ProcessKey(entry.instanceId(), entry.createdAt());
            if (processKey.partOf(sessionKey.getProcessKey())) {
                return entry;
            }
        }

        if (entry.projectId() != null) {
            projectAccessManager.assertAccess(entry.orgId(), entry.projectId(), null, ResourceAccessLevel.READER, false);
            return entry;
        }

        throw new UnauthorizedException("The current user (" + current.getUsername() + ") doesn't have " +
                                        "the necessary permissions to access process: " + entry.instanceId());
    }
}
