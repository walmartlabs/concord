package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.typesafe.config.ConfigException;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.ollie.config.Config;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import java.util.*;

public class PluginConfiguration {

    @Inject
    @Config("oidc.enabled")
    private boolean enabled;

    @Inject
    @Config("oidc.clientId")
    private String clientId;

    @Inject
    @Config("oidc.secret")
    private String secret;

    @Inject
    @Config("oidc.discoveryUri")
    private String discoveryUri;

    @Inject
    @Config("oidc.urlBase")
    private String urlBase;

    @Inject
    @Config("oidc.afterLoginUrl")
    private String afterLoginUrl;

    @Inject
    @Config("oidc.afterLogoutUrl")
    private String afterLogoutUrl;

    @Inject
    @Nullable
    @Config("oidc.scopes")
    private List<String> scopes;

    private final Map<UUID, TeamMapping> teamMapping;

    private final Map<String, List<Source>> roleMapping;

    @Inject
    public PluginConfiguration(@Config("oidc.teamMapping") Map<String, Map<String, Object>> teamMapping,
                               @Config("oidc.roleMapping") Map<String, Map<String, Object>> roleMapping) {
        this.teamMapping = toTeamMapping(teamMapping);
        this.roleMapping = toRoleMapping(roleMapping);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSecret() {
        return secret;
    }

    public String getDiscoveryUri() {
        return discoveryUri;
    }

    public String getUrlBase() {
        return urlBase;
    }

    public String getAfterLoginUrl() {
        return afterLoginUrl;
    }

    public String getAfterLogoutUrl() {
        return afterLogoutUrl;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public Map<UUID, TeamMapping> getTeamMapping() {
        return teamMapping;
    }

    public Map<String, List<Source>> getRoleMapping() {
        return roleMapping;
    }

    private static Map<UUID, TeamMapping> toTeamMapping(Map<String, Map<String, Object>> mapping) {
        Map<UUID, TeamMapping> m = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : mapping.entrySet()) {
            String path = "oidc.teamMapping." + e.getKey();
            m.put(toUUID(path, e.getKey()), new TeamMapping(getSources(path, e.getValue()), getTeamRole(path, "role", e.getValue(), TeamRole.MEMBER)));
        }
        return m;
    }

    private static Map<String, List<Source>> toRoleMapping(Map<String, Map<String, Object>> mapping) {
        Map<String, List<Source>> m = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : mapping.entrySet()) {
            String path = "oidc.roleMapping." + e.getKey();
            m.put(e.getKey(), getSources(path, e.getValue()));
        }
        return m;
    }

    private static UUID toUUID(String path, String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new RuntimeException(path + " invalid format '" + value + "'. expected UUID");
        }
    }

    private static List<Source> getSources(String path, Map<String, Object> m) {
        List<String> raw = assertList(path, "source", m);
        List<Source> result = new ArrayList<>(raw.size());
        for (String r : raw) {
            String[] sourcePattern = r.split("\\.", 2);
            if (sourcePattern.length != 2) {
                throw new RuntimeException(path + ".source invalid format '" + r + "'. expected source.pattern");
            }
            result.add(new Source(sourcePattern[0], sourcePattern[1]));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> assertList(String path, String key, Map<String, Object> m) {
        Object raw = m.get(key);
        if (raw == null) {
            throw new ConfigException.Missing(path + "." + key);
        }

        if (!(raw instanceof List)) {
            throw new RuntimeException(path + "." + key + " has type " + raw.getClass() + " rather than list");
        }
        return (List<String>) raw;
    }

    private static String getString(String path, String key, Map<String, Object> m) {
        Object raw = m.get(key);
        if (raw == null) {
            return null;
        }

        if (!(raw instanceof String)) {
            throw new RuntimeException(path + "." + key + " has type " + raw.getClass() + " rather than string");
        }
        return (String) raw;
    }

    private static TeamRole getTeamRole(String path, String key, Map<String, Object> m, TeamRole defaultRole) {
        String value = getString(path, key, m);
        if (value == null) {
            return defaultRole;
        }

        return asTeamRole(path + "." + key, value);
    }

    private static TeamRole asTeamRole(String path, String value) {
        try {
            return TeamRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(path + " unknown role: '" + value + "'. Available roles: " + Arrays.toString(TeamRole.values()));
        }
    }

    public static class Source {

        private final String attribute;

        private final String pattern;

        public Source(String attribute, String pattern) {
            this.attribute = attribute;
            this.pattern = pattern;
        }

        public String getAttribute() {
            return attribute;
        }

        public String getPattern() {
            return pattern;
        }
    }

    public static class TeamMapping {

        private final List<Source> sources;

        private final TeamRole role;

        public TeamMapping(List<Source> sources, TeamRole role) {
            this.sources = sources;
            this.role = role;
        }

        public List<Source> getSources() {
            return sources;
        }

        public TeamRole getRole() {
            return role;
        }
    }
}
