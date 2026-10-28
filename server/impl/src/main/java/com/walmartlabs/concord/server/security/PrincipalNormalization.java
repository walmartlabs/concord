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

import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.util.UUID;

/** Transitional bridge for authentication realms that still return legacy principals. */
public final class PrincipalNormalization {
    private PrincipalNormalization() {
    }

    public static SubjectPrincipal getCurrent() {
        if (ThreadContext.getSecurityManager() == null) {
            return null;
        }
        Subject subject = SecurityUtils.getSubject();
        return subject == null ? null : find(subject.getPrincipals());
    }

    public static SubjectPrincipal assertCurrent() {
        SubjectPrincipal subject = getCurrent();
        if (subject == null) {
            throw new AuthenticationException("Can't determine the current principal (" + SubjectPrincipal.class.getName() + ")");
        }
        return subject;
    }

    /** Returns the SDK identity, rejecting collections containing different account IDs. */
    public static SubjectPrincipal find(PrincipalCollection principals) {
        if (principals == null) {
            return null;
        }
        SubjectPrincipal sdk = null;
        UserPrincipal legacy = null;
        UUID id = null;
        for (Object principal : principals) {
            UUID candidate = null;
            if (principal instanceof SubjectPrincipal) {
                SubjectPrincipal subject = (SubjectPrincipal) principal;
                candidate = subject.userId();
                if (sdk == null) {
                    sdk = subject;
                }
            } else if (principal instanceof UserPrincipal) {
                UserPrincipal user = (UserPrincipal) principal;
                candidate = user.getId();
                if (legacy == null) {
                    legacy = user;
                }
            }
            if (candidate != null) {
                if (id != null && !id.equals(candidate)) {
                    throw new IllegalArgumentException("Conflicting user identities in principal collection");
                }
                id = candidate;
            }
        }
        if (sdk != null || legacy == null) {
            return sdk;
        }
        return new SubjectPrincipal(legacy.getId(), legacy.getUsername(), legacy.getDomain(),
                new AuthenticationContext(legacy.getRealm(), null, null));
    }

    /** Adds the normalized principal without removing legacy or credential principals. */
    public static PrincipalCollection normalize(PrincipalCollection principals) {
        SubjectPrincipal subject = find(principals);
        if (subject == null || !principals.byType(SubjectPrincipal.class).isEmpty()) {
            return principals;
        }
        SimplePrincipalCollection result = new SimplePrincipalCollection();
        result.addAll(principals);
        result.add(subject, subject.authentication().providerId());
        return result;
    }
}
