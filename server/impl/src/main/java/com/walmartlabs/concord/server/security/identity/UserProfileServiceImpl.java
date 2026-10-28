package com.walmartlabs.concord.server.security.identity;

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

import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.UserDirectory;
import com.walmartlabs.concord.server.sdk.security.UserProfile;
import com.walmartlabs.concord.server.sdk.security.UserProfileService;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.UUID;

/** Resolves presentation data independently of authentication provenance. */
public class UserProfileServiceImpl implements UserProfileService {
    private final IdentityDao identities;
    private final ProviderRegistry registry;

    @Inject
    public UserProfileServiceImpl(IdentityDao identities, ProviderRegistry registry) {
        this.identities = identities;
        this.registry = registry;
    }

    @Override
    public UserProfile get(UUID userId) {
        IdentityDao.ProfileSelection selection = identities.profile(userId)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + userId, Response.Status.NOT_FOUND));
        if ("local".equals(selection.providerId()) || selection.identity() == null) {
            return selection.stored();
        }
        UserDirectory directory = registry.directories().get(selection.providerId());
        if (directory == null || !directory.acceptsIdentity(selection.identity())) {
            return selection.stored();
        }
        try {
            return directory.get(selection.identity()).orElse(selection.stored());
        } catch (RuntimeException e) {
            // Presentation failures must not become an authorization decision.
            return selection.stored();
        }
    }
}
