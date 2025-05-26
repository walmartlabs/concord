package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.server.security.internal.InternalRealm;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import javax.inject.Inject;
import java.util.UUID;
import java.util.concurrent.Callable;

public class UserSecurityContext {

    private final SecurityManager securityManager;
    private final UserManager userManager;

    @Inject
    public UserSecurityContext(SecurityManager securityManager, UserManager userManager) {
        this.securityManager = securityManager;
        this.userManager = userManager;
    }

    /**
     * Run the specified {@link Callable} as if it was started by another user.
     */
    public <T> T runAs(UUID userID, Callable<T> c) throws Exception {
        UserEntry u = userManager.get(userID).orElse(null);
        if (u == null) {
            throw new UnauthorizedException("User '" + userID + "'not found");
        }

        try {
            ThreadContext.bind(securityManager);

            SimplePrincipalCollection principals = new SimplePrincipalCollection();
            principals.add(new UserPrincipal(InternalRealm.REALM_NAME, u), InternalRealm.REALM_NAME);

            Subject subject = new Subject.Builder()
                    .sessionCreationEnabled(false)
                    .authenticated(true)
                    .principals(principals)
                    .buildSubject();

            ThreadContext.bind(subject);

            return c.call();
        } finally {
            ThreadContext.unbindSubject();
            ThreadContext.unbindSecurityManager();
        }
    }
}
