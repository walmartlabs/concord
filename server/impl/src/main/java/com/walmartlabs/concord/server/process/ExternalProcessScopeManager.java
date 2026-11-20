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

import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityException;
import com.walmartlabs.concord.server.security.ExternalIdentityDelegationManager;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.ExternalIdentityLifecycleManager;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.subject.PrincipalCollection;

import javax.inject.Inject;
import java.util.List;

/**
 * Restoration checks deliberately run outside the process-principal cache.
 * External execution remains denied until resource enforcement is complete.
 */
public class ExternalProcessScopeManager {

    private final ExternalProcessScopeDao dao;
    private final ExternalIdentityLifecycleManager lifecycleManager;
    private final ExternalIdentityDelegationManager delegationManager;

    @Inject
    public ExternalProcessScopeManager(ExternalProcessScopeDao dao, ExternalIdentityLifecycleManager lifecycleManager,
                                       ExternalIdentityDelegationManager delegationManager) {
        this.dao = dao;
        this.lifecycleManager = lifecycleManager;
        this.delegationManager = delegationManager;
    }

    public void assertRestorationAllowed(PartialProcessKey key, PrincipalCollection principals) {
        var stored = dao.get(key).orElse(null);
        var users = principals == null ? List.<UserPrincipal>of() : principals.byType(UserPrincipal.class);
        var scope = stored == null ? null : stored.scope();
        if (scope == null) {
            if ((stored != null && stored.userType() == UserType.EXTERNAL)
                    || users.stream().anyMatch(user -> user.getType() == UserType.EXTERNAL)) {
                throw new UnauthorizedException("External process has no trusted delegation scope");
            }
            return;
        }
        var repositoryMatches = scope.repositoryId().equals(stored.repositoryId())
                || (stored.repositoryId() == null && stored.parentId() != null);
        if (users.size() != 1 || stored.userType() != UserType.EXTERNAL
                || !scope.userId().equals(stored.initiatorId())
                || !scope.userId().equals(users.iterator().next().getId())
                || users.iterator().next().getType() != UserType.EXTERNAL
                || !scope.projectId().equals(stored.projectId()) || !repositoryMatches) {
            throw new UnauthorizedException("Saved process subject does not match its trusted delegation scope");
        }
        dao.assertSourceIntegration(scope);
        try {
            if (!scope.userId().equals(lifecycleManager.requireActive(scope.identity()))) {
                throw new UnauthorizedException("Process delegation identity binding changed");
            }
        } catch (ExternalIdentityException e) {
            throw new UnauthorizedException("External process lifecycle verification failed: " + e.getMessage());
        }
        delegationManager.requirePolicy(scope.identity(), scope.orgId(), scope.projectId(), scope.repositoryId());
        throw new UnauthorizedException("External process execution is disabled pending resource delegation enforcement");
    }
}
