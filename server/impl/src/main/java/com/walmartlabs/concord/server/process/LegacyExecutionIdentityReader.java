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

import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.subject.PrincipalCollection;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Collection;

/** Compatibility reader for principal collections written before the identity format. */
public final class LegacyExecutionIdentityReader {
    private LegacyExecutionIdentityReader() {
    }

    public static ExecutionIdentity read(byte[] data) {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(data))) {
            Object root = input.readObject();
            try {
                input.readObject();
                throw new IllegalArgumentException("Trailing object in legacy execution identity");
            } catch (EOFException expected) {
                // Exactly one serialized collection is expected.
            }
            Iterable<?> principals;
            if (root instanceof PrincipalCollection) {
                principals = (PrincipalCollection) root;
            } else if (root instanceof Collection<?>) {
                principals = (Collection<?>) root;
            } else {
                throw new IllegalArgumentException("Legacy execution identity is not a principal collection");
            }
            ExecutionIdentity identity = null;
            for (Object principal : principals) {
                ExecutionIdentity candidate = null;
                if (principal instanceof UserPrincipal) {
                    UserPrincipal user = (UserPrincipal) principal;
                    candidate = new ExecutionIdentity(user.getId(), user.getRealm());
                } else if (principal instanceof SubjectPrincipal) {
                    SubjectPrincipal user = (SubjectPrincipal) principal;
                    candidate = new ExecutionIdentity(user.userId(), user.authentication().providerId());
                }
                if (candidate != null) {
                    if (identity != null && !identity.equals(candidate)) {
                        throw new IllegalArgumentException("Conflicting identities in legacy principal collection");
                    }
                    identity = candidate;
                }
            }
            if (identity == null) {
                throw new IllegalArgumentException("No user identity in legacy principal collection");
            }
            return identity;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Missing class in legacy principal collection: " + e.getMessage(), e);
        } catch (IOException | NullPointerException e) {
            throw new IllegalArgumentException("Corrupt legacy principal collection", e);
        }
    }
}
