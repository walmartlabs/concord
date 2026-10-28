package com.walmartlabs.concord.server.security;

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

import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;

import java.util.UUID;

/** Authorization snapshots are confined to a request or an internal execution scope. */
public final class AuthorizationScope implements AutoCloseable {
    private static final ThreadLocal<AuthorizationScope> CURRENT = new ThreadLocal<>();

    private final AuthorizationScope previous;
    private final UUID userId;
    private final AuthorizationService.EffectiveAuthorization authorization;

    private AuthorizationScope(AuthorizationService service, SubjectPrincipal principal, boolean newAuthentication) {
        previous = CURRENT.get();
        userId = principal.userId();
        authorization = service.prepare(userId, newAuthentication);
        CURRENT.set(this);
    }

    public static AuthorizationScope open(AuthorizationService service, SubjectPrincipal principal, boolean newAuthentication) {
        return new AuthorizationScope(service, principal, newAuthentication);
    }

    public static AuthorizationService.EffectiveAuthorization current(UUID userId) {
        AuthorizationScope scope = CURRENT.get();
        return scope != null && scope.userId.equals(userId) ? scope.authorization : null;
    }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
