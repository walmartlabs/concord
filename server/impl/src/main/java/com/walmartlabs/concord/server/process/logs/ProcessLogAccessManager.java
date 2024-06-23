package com.walmartlabs.concord.server.process.logs;

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

import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;

import javax.inject.Inject;
import java.util.UUID;

public class ProcessLogAccessManager {

    private final ProjectAccessManager projectAccessManager;
    private final ProcessManager processManager;
    private final ProcessConfiguration processCfg;

    @Inject
    public ProcessLogAccessManager(ProjectAccessManager projectAccessManager,
                                   ProcessManager processManager,
                                   ProcessConfiguration processCfg) {

        this.projectAccessManager = projectAccessManager;
        this.processManager = processManager;
        this.processCfg = processCfg;
    }

    public ProcessKey assertLogAccess(UUID instanceId) {
        ProcessEntry pe = processManager.assertProcess(instanceId);
        ProcessKey pk = new ProcessKey(pe.instanceId(), pe.createdAt());

        if (!processCfg.isCheckLogPermissions()) {
            return pk;
        }

        if (Roles.isAdmin() || Roles.isGlobalReader()) {
            return pk;
        }

        UserPrincipal principal = UserPrincipal.assertCurrent();

        UUID initiatorId = pe.initiatorId();
        if (principal.getId().equals(initiatorId)) {
            // process owners should be able to view the process' logs
            return pk;
        }

        SessionKeyPrincipal s = SessionKeyPrincipal.getCurrent();
        if (s != null && pk.partOf(s.getProcessKey())) {
            // processes can access their own logs
            return pk;
        }

        if (pe.projectId() != null) {
            projectAccessManager.assertAccess(pe.projectId(), ResourceAccessLevel.WRITER, true);
            return pk;
        }

        throw new UnauthorizedException("The current user (" + principal.getUsername() + ") doesn't have " +
                "the necessary permissions to view the process log: " + instanceId);
    }
}
