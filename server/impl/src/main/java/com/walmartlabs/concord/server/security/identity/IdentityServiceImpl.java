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

import com.typesafe.config.Config;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.*;
import com.walmartlabs.concord.server.security.legacy.LegacyUserLookup;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import com.walmartlabs.concord.server.user.UserManager;

import javax.inject.Inject;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import java.util.*;

/** Resolves verified authentication identities without treating presentation names as account keys. */
public class IdentityServiceImpl implements IdentityService {
    private final IdentityDao dao;
    private final ProviderRegistry registry;
    private final LegacyUserLookup legacy;
    private final UserManager users;
    private final Config config;

    @Inject
    public IdentityServiceImpl(IdentityDao dao, ProviderRegistry registry, LegacyUserLookup legacy, UserManager users, Config config) {
        this.dao = dao;
        this.registry = registry;
        this.legacy = legacy;
        this.users = users;
        this.config = config;
    }

    @Override
    public SubjectPrincipal resolve(VerifiedIdentity verified, AuthenticationContext authentication) {
        ExternalIdentityKey primary = verified.identity();
        if (!primary.providerId().equals(authentication.providerId())) throw new AuthenticationException("Authentication provider does not match identity");
        ProvisioningPolicy policy = registry.policies().get(primary.providerId());
        if (policy == null) throw new AuthenticationException("No provisioning policy for identity");
        Optional<IdentityDao.IdentityRecord> exact = dao.find(primary);
        if (exact.isPresent()) {
            IdentityDao.IdentityRecord record = exact.get();
            if (!record.active() || record.userId() == null) throw new AuthenticationException("Identity is retired");
            dao.observe(record.userId(), verified);
            return forUser(record.userId(), authentication);
        }

        Map<String, ExternalIdentityKey> bySource = new HashMap<>();
        Set<String> unavailable = new HashSet<>();
        Set<ExternalIdentityKey> identities = new LinkedHashSet<>();
        UserDirectory profileDirectory = registry.directories().get(policy.profileProviderId());
        ExternalIdentityKey profileIdentity = !"local".equals(policy.profileProviderId()) && profileDirectory.acceptsIdentity(primary) ? primary : null;
        for (IdentityLookup lookup : verified.identityLookups()) {
            if (!policy.allowedIdentityDirectories().contains(lookup.directoryId()) ||
                    !policy.authorizationSources().containsAll(lookup.authorizationSources()) ||
                    (lookup.forProfile() && !policy.profileProviderId().equals(lookup.directoryId()))) {
                throw new AuthenticationException("Lookup is not allowed by provisioning policy");
            }
            UserDirectory directory = registry.directories().get(lookup.directoryId());
            Optional<DirectoryEntry> result;
            try {
                result = directory.find(lookup.query());
            } catch (DirectoryAmbiguousException e) {
                throw new AuthenticationException("Ambiguous directory identity", e);
            } catch (DirectoryUnavailableException e) {
                unavailable.addAll(lookup.authorizationSources());
                continue;
            }
            if (result.isEmpty()) continue;
            ExternalIdentityKey identity = result.get().identity();
            if (!directory.acceptsIdentity(identity)) throw new AuthenticationException("Directory returned an invalid identity");
            identities.add(identity);
            if (lookup.forProfile()) {
                if (profileIdentity != null && !profileIdentity.equals(identity)) throw new AuthenticationException("Ambiguous profile identity");
                profileIdentity = identity;
            }
            for (String source : lookup.authorizationSources()) {
                AuthorizationProvider provider = registry.authorizationSources().get(source);
                if (!provider.acceptsIdentity(identity)) throw new AuthenticationException("Directory identity is not accepted by source");
                ExternalIdentityKey old = bySource.putIfAbsent(source, identity);
                if (old != null && !old.equals(identity)) throw new AuthenticationException("Ambiguous source identity");
            }
        }
        List<IdentityDao.Binding> bindings = new ArrayList<>();
        for (String source : policy.authorizationSources()) {
            AuthorizationProvider provider = registry.authorizationSources().get(source);
            boolean required = required(source);
            ExternalIdentityKey selected = bySource.get(source);
            if (provider.acceptsIdentity(primary)) {
                if (selected != null && !selected.equals(primary)) throw new AuthenticationException("Ambiguous source identity");
                selected = primary;
            }
            if (selected == null && !provider.acceptsIdentity(null)) {
                if (required) {
                    Response.Status status = unavailable.contains(source) ? Response.Status.SERVICE_UNAVAILABLE : Response.Status.FORBIDDEN;
                    throw new ConcordApplicationException("Required authorization identity unavailable: " + source, status);
                }
                continue;
            }
            if (!provider.isActive()) {
                if (required) throw new ConcordApplicationException("Required authorization source unavailable: " + source, Response.Status.SERVICE_UNAVAILABLE);
                continue;
            }
            bindings.add(new IdentityDao.Binding(source, selected, required));
        }
        try {
            IdentityDao.Resolution resolution = dao.resolve(verified, policy, legacy.selector(verified).orElse(null), new ArrayList<>(identities), bindings, profileIdentity);
            if (resolution.created()) users.auditIdentityCreation(resolution.userId());
            dao.observe(resolution.userId(), verified);
            return forUser(resolution.userId(), authentication);
        } catch (ClientErrorException e) {
            if (e.getResponse().getStatus() == 409) throw new AuthenticationException("Identity ownership conflict", e);
            throw e;
        }
    }

    private boolean required(String source) {
        String path = "security.authorization.sources." + source + ".required";
        String qualified = "com.walmartlabs.concord.server." + path;
        if (config.hasPath(qualified)) return config.getBoolean(qualified);
        return config.hasPath(path) && config.getBoolean(path);
    }

    @Override
    public SubjectPrincipal forUser(UUID userId, AuthenticationContext authentication) {
        dao.enabledUser(userId);
        IdentityDao.ProfileSelection profile = dao.profile(userId).orElseThrow(() -> new AuthenticationException("Account not found"));
        return new SubjectPrincipal(userId, profile.stored().username(), profile.stored().domain(), authentication);
    }
}
