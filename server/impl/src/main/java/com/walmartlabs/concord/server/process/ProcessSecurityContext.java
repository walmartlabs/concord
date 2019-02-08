package com.walmartlabs.concord.server.process;

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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Injector;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.security.PrincipalUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.internal.InternalRealm;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Named
public class ProcessSecurityContext {

    private static final String PRINCIPAL_FILE_PATH = ".concord/current_user";

    private final ProcessStateManager stateManager;
    private final Injector injector;
    private final Cache<PartialProcessKey, PrincipalCollection> principalCache;
    private final UserManager userManager;

    @Inject
    public ProcessSecurityContext(ProcessStateManager stateManager, Injector injector, UserManager userManager) {
        this.stateManager = stateManager;
        this.injector = injector;
        this.userManager = userManager;
        this.principalCache = CacheBuilder.newBuilder()
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .build();
    }

    public void storeCurrentSubject(ProcessKey processKey) {
        Subject s = SecurityUtils.getSubject();

        PrincipalCollection src = s.getPrincipals();

        // filter out transient principals
        SimplePrincipalCollection dst = new SimplePrincipalCollection();
        for (String realm : src.getRealmNames()) {
            Collection ps = src.fromRealm(realm);
            for (Object p : ps) {
                if (p instanceof SessionKeyPrincipal) {
                    continue;
                }

                dst.add(p, realm);
            }
        }

        stateManager.replace(processKey, PRINCIPAL_FILE_PATH, PrincipalUtils.serialize(dst));
    }

    public PrincipalCollection getPrincipals(PartialProcessKey processKey) {
        try {
            return principalCache.get(processKey, () -> doGetPrincipals(processKey));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private PrincipalCollection doGetPrincipals(PartialProcessKey processKey) {
        return stateManager.get(processKey, PRINCIPAL_FILE_PATH, PrincipalUtils::deserialize)
                .orElse(null);
    }

    public <T> T runAs(UUID userID, Callable<T> c) throws Exception {
        UserEntry u = userManager.get(userID).orElse(null);
        if (u == null) {
            throw new UnauthorizedException("User '" + userID + "'not found");
        }

        SecurityManager securityManager = injector.getInstance(SecurityManager.class);
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
    }
}
