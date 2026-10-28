package com.walmartlabs.concord.server.plugins.oidc;

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
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.security.IdentityService;
import com.walmartlabs.concord.server.sdk.security.VerifiedIdentity;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Authenticates only verified userinfo; all grants are supplied by authorization sources. */
public class OidcRealm extends AuthenticatingRealm {
    private final IdentityService identities;

    @Inject
    public OidcRealm(IdentityService identities) {
        this.identities = identities;
        setName("oidc");
        setCredentialsMatcher((token, info) -> token == info.getCredentials());
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof OidcToken;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        var envelope = ((OidcToken) token).getVerifiedProfile();
        if (envelope == null || envelope.expiresAt() == null || !Instant.now().isBefore(envelope.expiresAt())) {
            throw new org.apache.shiro.authc.AuthenticationException("OIDC verification expired");
        }
        var profile = envelope.profile();
        if (profile == null || profile.id() == null || profile.id().isBlank() || envelope.issuer() == null || envelope.issuer().isBlank()) {
            throw new org.apache.shiro.authc.AuthenticationException("OIDC issuer or subject missing");
        }
        String username = profile.email() == null || profile.email().isBlank() ? profile.id() : profile.email().toLowerCase();
        Map<String, Object> attributes = profile.attributes() == null ? Map.of() : profile.attributes();
        var presentation = new com.walmartlabs.concord.server.sdk.security.UserProfile(username, null, profile.displayName(), profile.email(), attributes);
        var verified = new VerifiedIdentity(new ExternalIdentityKey("oidc", envelope.issuer(), profile.id()), presentation,
                Set.of(), attributes, envelope.observedAt(), envelope.expiresAt());
        var principal = identities.resolve(verified, new AuthenticationContext("oidc", envelope.observedAt(), null));
        return new SimpleAuthenticationInfo(principal, token, getName());
    }
}
