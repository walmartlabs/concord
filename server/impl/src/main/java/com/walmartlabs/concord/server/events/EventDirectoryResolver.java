/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.DirectoryEntry;
import com.walmartlabs.concord.server.sdk.security.DirectoryQuery;
import com.walmartlabs.concord.server.sdk.security.DirectoryUnavailableException;
import com.walmartlabs.concord.server.sdk.security.IdentityService;
import com.walmartlabs.concord.server.sdk.security.UserDirectory;
import com.walmartlabs.concord.server.sdk.security.VerifiedIdentity;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Resolves an event-supplied directory attribute into a provisioned account. */
public class EventDirectoryResolver {
    private final ProviderRegistry registry;
    private final IdentityService identities;
    private final UserManager users;

    @Inject
    public EventDirectoryResolver(ProviderRegistry registry, IdentityService identities, UserManager users) {
        this.registry = registry;
        this.identities = identities;
        this.users = users;
    }

    public Optional<UserEntry> find(String directoryId, String attribute, String value) {
        UserDirectory directory = registry.directories().get(directoryId);
        if (directory == null) {
            throw new ConcordApplicationException("Event user directory unavailable: " + directoryId, Response.Status.SERVICE_UNAVAILABLE);
        }
        Optional<DirectoryEntry> entry;
        try {
            entry = directory.find(new DirectoryQuery(attribute, value, null));
        } catch (DirectoryUnavailableException e) {
            throw new ConcordApplicationException("Event user directory unavailable: " + directoryId, e, Response.Status.SERVICE_UNAVAILABLE);
        }
        return entry.map(result -> {
            if (!directory.acceptsIdentity(result.identity())) {
                throw new ConcordApplicationException("Event directory returned an invalid identity: " + directoryId);
            }
            VerifiedIdentity verified = new VerifiedIdentity(result.identity(), result.profile(), Set.of(), Map.of(), null, null);
            var subject = identities.resolve(verified, new AuthenticationContext(result.identity().providerId(), null, null));
            return users.get(subject.userId()).orElseThrow(() -> new ConcordApplicationException("Event user not found: " + subject.userId()));
        });
    }
}
