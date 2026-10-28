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

import com.walmartlabs.concord.common.Matcher;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.plugins.oidc.PluginConfiguration.Source;
import com.walmartlabs.concord.server.role.RoleDao;
import com.walmartlabs.concord.server.sdk.security.AuthorizationData;
import com.walmartlabs.concord.server.sdk.security.AuthorizationProvider;
import com.walmartlabs.concord.server.sdk.security.AuthorizationRequest;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.security.TeamGrant;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Maps retained, verified OIDC claims to this source's complete set of grants. */
public class OidcAuthorizationProvider implements AuthorizationProvider {
    private final PluginConfiguration configuration;
    private final RoleDao roleDao;
    private final TeamDao teamDao;

    @Inject
    public OidcAuthorizationProvider(PluginConfiguration configuration, RoleDao roleDao, TeamDao teamDao) {
        this.configuration = configuration;
        this.roleDao = roleDao;
        this.teamDao = teamDao;
    }

    @Override
    public String id() {
        return "oidc";
    }

    @Override
    public boolean isActive() {
        return configuration.isEnabled();
    }

    @Override
    public boolean acceptsIdentity(ExternalIdentityKey identity) {
        return identity != null && id().equals(identity.providerId()) && !identity.authority().isBlank()
                && !identity.subject().isBlank();
    }

    @Override
    public Duration defaultMaxAge() {
        return Duration.ofHours(1);
    }

    @Override
    public AuthorizationData resolve(AuthorizationRequest request) {
        if (!isActive() || !acceptsIdentity(request.identity()) || request.attributesObservedAt() == null
                || request.attributesExpireAt() == null || !request.attributesExpireAt().isAfter(Instant.now())) {
            throw new AuthorizationUnavailableException("OIDC verified claims are unavailable or expired");
        }
        Map<String, Object> attributes = request.verifiedAttributes();
        Set<String> roles = new HashSet<>();
        configuration.getRoleMapping().forEach((role, sources) -> {
            if (role != null && !role.isBlank() && valid(sources) && roleDao.getId(role) != null && matches(attributes, sources)) {
                roles.add(role);
            }
        });
        Set<TeamGrant> teams = new HashSet<>();
        configuration.getTeamMapping().forEach((teamId, mapping) -> {
            if (teamId != null && mapping != null && mapping.role() != null && valid(mapping.sources())
                    && teamDao.get(teamId) != null && matches(attributes, mapping.sources())) {
                teams.add(new TeamGrant(teamId, mapping.role().name()));
            }
        });
        return new AuthorizationData(Set.of(), roles, teams, request.attributesExpireAt());
    }

    private static boolean valid(List<Source> sources) {
        if (sources == null) {
            return false;
        }
        for (Source source : sources) {
            if (source == null || source.attribute() == null || source.attribute().isBlank() || source.pattern() == null) {
                return false;
            }
            try {
                Pattern.compile(source.pattern());
            } catch (PatternSyntaxException e) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(Map<String, Object> attributes, List<Source> sources) {
        for (Source source : sources) {
            Object value = attributes.get(source.attribute());
            if (Matcher.matches(value, source.pattern())) {
                return true;
            }
        }
        return false;
    }
}
