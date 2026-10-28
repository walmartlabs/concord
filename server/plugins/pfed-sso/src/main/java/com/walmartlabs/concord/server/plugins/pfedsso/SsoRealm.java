package com.walmartlabs.concord.server.plugins.pfedsso;

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

import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.security.*;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SsoRealm extends AuthenticatingRealm {
    public static final String REALM_NAME = "sso";

    private final IdentityService identities;
    private final ProviderRegistry registry;
    private final SsoConfiguration cfg;
    private final AuditLog auditLog;

    @Inject
    public SsoRealm(IdentityService identities, ProviderRegistry registry, SsoConfiguration cfg, AuditLog auditLog) {
        this.identities = identities;
        this.registry = registry;
        this.cfg = cfg;
        this.auditLog = auditLog;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof SsoToken;
    }

    @Override
    @WithTimer
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        SsoToken t = (SsoToken) token;
        if (t.verifiedSubject() == null || t.verifiedSubject().isBlank() || t.getUsername() == null || cfg.getIdentityAuthority() == null) {
            return null;
        }
        ExternalIdentityKey key = new ExternalIdentityKey(REALM_NAME, cfg.getIdentityAuthority(), t.verifiedSubject());
        UserProfile profile = new UserProfile(t.getUsername(), t.getDomain(), t.getDisplayName(), t.getMail(), Map.of());
        ProvisioningPolicy policy = registry.policies().get(REALM_NAME);
        String directory = cfg.getIdentityDirectory();
        Set<IdentityLookup> lookups = Set.of();
        if (directory != null && !directory.isBlank()) {
            Set<String> sources = policy.authorizationSources().stream().filter(id -> {
                AuthorizationProvider source = registry.authorizationSources().get(id);
                return !source.acceptsIdentity(key) && !source.acceptsIdentity(null);
            }).collect(Collectors.toUnmodifiableSet());
            boolean forProfile = directory.equals(policy.profileProviderId());
            if (forProfile || !sources.isEmpty()) {
                lookups = Set.of(new IdentityLookup(directory, new DirectoryQuery("username", t.getUsername(), t.getDomain()), sources, forProfile));
            }
        }
        VerifiedIdentity verified = new VerifiedIdentity(key, profile, lookups, Map.of(), null, null);
        SubjectPrincipal principal = identities.resolve(verified, new AuthenticationContext(REALM_NAME, Instant.now(), null));
        auditLog.add(AuditObject.SYSTEM, AuditAction.ACCESS)
                .userId(principal.userId())
                .field("username", principal.username())
                .field("userDomain", principal.domain())
                .field("realm", REALM_NAME)
                .log();
        return new SimpleAuthenticationInfo(principal, t.getCredentials(), getName());
    }
}
