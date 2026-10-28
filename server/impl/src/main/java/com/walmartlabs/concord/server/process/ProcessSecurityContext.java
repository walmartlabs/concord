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

import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.IdentityService;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.AuthorizationScope;
import com.walmartlabs.concord.server.security.SecurityUtils;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

public class ProcessSecurityContext {

    private static final String PRINCIPAL_FILE_PATH = ".concord/current_user";

    private final SecurityManager securityManager;
    private final ProcessStateManager stateManager;
    private final AuthorizationService authorizationService;
    private final IdentityService identityService;

    @Inject
    public ProcessSecurityContext(SecurityManager securityManager, ProcessStateManager stateManager,
                                  AuthorizationService authorizationService, IdentityService identityService) {
        this.securityManager = securityManager;
        this.stateManager = stateManager;
        this.authorizationService = authorizationService;
        this.identityService = identityService;
    }

    public byte[] serializePrincipals(PrincipalCollection src) {
        SubjectPrincipal principal = src != null ? src.oneByType(SubjectPrincipal.class) : null;
        if (principal == null) {
            throw new UnauthorizedException("Process has no user identity");
        }
        return ExecutionIdentityCodec.encode(new ExecutionIdentity(principal.userId(), principal.authentication().providerId()));
    }

    public static Optional<ExecutionIdentity> readIdentity(InputStream input) {
        try {
            return Optional.of(ExecutionIdentityCodec.decode(input.readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Compares current security principal to last-saved principal for the
     * specified process. If they are different, it means that the user has changed.
     */
    public boolean hasChanged(ProcessKey processKey) {
        SubjectPrincipal current = SecurityUtils.getSubject().getPrincipals().oneByType(SubjectPrincipal.class);
        PrincipalCollection previousPrincipals = getPrincipals(processKey);
        SubjectPrincipal previous = previousPrincipals != null ? previousPrincipals.oneByType(SubjectPrincipal.class) : null;

        if (current == null || previous == null) {
            return true; // something's funky if either is null. should we throw an exception?
        }

        UUID currentUserId = current.userId();
        UUID previousUserId = previous.userId();

        return !previousUserId.equals(currentUserId);
    }

    public void storeCurrentSubject(ProcessKey processKey) {
        Subject s = SecurityUtils.getSubject();
        PrincipalCollection src = s.getPrincipals();
        storeSubject(processKey, src);
    }

    public void storeSubject(ProcessKey processKey, PrincipalCollection src) {
        stateManager.replace(processKey, PRINCIPAL_FILE_PATH, serializePrincipals(src));
    }

    public void storeIdentity(ProcessKey processKey, ExecutionIdentity identity) {
        stateManager.replace(processKey, PRINCIPAL_FILE_PATH, ExecutionIdentityCodec.encode(identity));
    }

    public PrincipalCollection getPrincipals(PartialProcessKey processKey) {
        ExecutionIdentity identity = stateManager.get(processKey, PRINCIPAL_FILE_PATH, ProcessSecurityContext::readIdentity)
                .orElseThrow(() -> new UnauthorizedException("Process has no current user: " + processKey));
        SubjectPrincipal principal = identityService.forUser(identity.userId(),
                new AuthenticationContext(identity.authenticationProviderId(), null, null));
        return new SimplePrincipalCollection(principal, identity.authenticationProviderId());
    }

    /**
     * Run the specified {@link Callable} as if it was started by the current user (e.g. initiator)
     * of the specified process.
     */
    public <T> T runAsCurrentUser(ProcessKey processKey, Callable<T> c) throws Exception {
        PrincipalCollection principals = getPrincipals(processKey);
        SubjectPrincipal principal = principals != null ? principals.oneByType(SubjectPrincipal.class) : null;
        if (principal == null) {
            throw new UnauthorizedException("Process has no current user: " + processKey);
        }

        Subject previousSubject = ThreadContext.getSubject();
        SecurityManager previousManager = ThreadContext.getSecurityManager();
        try (AuthorizationScope ignored = AuthorizationScope.open(authorizationService, principal, false)) {
            ThreadContext.bind(securityManager);
            Subject subject = new Subject.Builder()
                    .sessionCreationEnabled(false)
                    .authenticated(true)
                    .principals(principals)
                    .buildSubject();
            ThreadContext.bind(subject);
            return c.call();
        } finally {
            if (previousSubject == null) {
                ThreadContext.unbindSubject();
            } else {
                ThreadContext.bind(previousSubject);
            }
            if (previousManager == null) {
                ThreadContext.unbindSecurityManager();
            } else {
                ThreadContext.bind(previousManager);
            }
        }
    }
}
