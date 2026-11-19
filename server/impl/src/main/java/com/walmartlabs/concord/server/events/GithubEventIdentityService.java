package com.walmartlabs.concord.server.events;

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

import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.identity.ExternalEventIdentityResolver;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityException;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityResolution;
import com.walmartlabs.concord.server.security.GithubAuthenticatingFilter;
import jakarta.servlet.http.HttpServletRequest;

import javax.inject.Inject;
import java.util.Set;

public class GithubEventIdentityService {

    private final Set<ExternalEventIdentityResolver> resolvers;
    private final String authority;

    @Inject
    public GithubEventIdentityService(GithubConfiguration cfg, Set<ExternalEventIdentityResolver> resolvers) {
        this.resolvers = Set.copyOf(resolvers);
        this.authority = cfg.getExternalIdentityAuthority();
        if (!resolvers.isEmpty() && (authority == null || authority.isBlank() || authority.length() > 512)) {
            throw new IllegalArgumentException("github.externalIdentityAuthority must contain 1 to 512 characters when external event identity resolvers are registered");
        }
    }

    public void assertInitiatorAllowed(HttpServletRequest request, String eventType, String deliveryId) {
        if (resolvers.isEmpty()) {
            return;
        }

        var context = GithubAuthenticatingFilter.getEventContext(request, authority, eventType, deliveryId);
        var claimed = false;
        try {
            for (var resolver : resolvers) {
                var resolution = resolver.resolve(context);
                if (resolution == null) {
                    throw new ConcordApplicationException("External event identity resolver returned null");
                }
                if (resolution instanceof ExternalIdentityResolution.Rejected rejected) {
                    throw new ConcordApplicationException("GitHub event actor rejected: " + rejected.reason());
                }
                if (resolution instanceof ExternalIdentityResolution.Resolved resolved) {
                    if (!authority.equals(resolved.identity().authority())) {
                        throw new ConcordApplicationException("Resolved GitHub identity has a different authority");
                    }
                    if (claimed) {
                        throw new ConcordApplicationException("Multiple external event identity resolvers claimed the GitHub actor");
                    }
                    claimed = true;
                }
            }
        } catch (ExternalIdentityException e) {
            throw new ConcordApplicationException("GitHub event actor verification failed", e);
        }

        if (claimed) {
            throw new ConcordApplicationException("External GitHub actor execution is disabled pending lifecycle, freshness and restricted-delegation enforcement");
        }
    }
}
