package com.walmartlabs.concord.server.security.ldap;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.sdk.security.DirectoryAmbiguousException;
import com.walmartlabs.concord.server.sdk.security.DirectoryUnavailableException;
import com.walmartlabs.concord.server.sdk.security.DirectoryEntry;
import com.walmartlabs.concord.server.sdk.security.DirectoryQuery;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.security.UserDirectory;
import com.walmartlabs.concord.server.sdk.security.UserProfile;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** LDAP's name-based identity namespace, independent of password authentication. */
public class LdapUserDirectory implements UserDirectory {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LdapManager manager;

    @Inject
    public LdapUserDirectory(LdapManager manager) {
        this.manager = manager;
    }

    @Override
    public String id() {
        return "ldap";
    }

    @Override
    public boolean acceptsIdentity(ExternalIdentityKey identity) {
        if (identity == null || !id().equals(identity.providerId()) || !identity.authority().isEmpty()) {
            return false;
        }
        try {
            JsonNode value = JSON.readTree(identity.subject());
            if (value == null || !value.isArray() || value.size() != 2 || !value.get(0).isTextual()
                    || value.get(0).textValue().isEmpty() || !(value.get(1).isTextual() || value.get(1).isNull())) {
                return false;
            }
            return key(value.get(0).textValue(), value.get(1).isNull() ? null : value.get(1).textValue()).equals(identity);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public Optional<DirectoryEntry> find(DirectoryQuery query) {
        try {
            LdapPrincipal principal = switch (query.attribute()) {
                case "username" -> manager.getPrincipal(query.value(), query.domain());
                case "email" -> manager.getPrincipalByMail(query.value());
                case "distinguishedName" -> manager.getPrincipalByDn(query.value());
                default -> throw new IllegalArgumentException("Unsupported LDAP directory attribute: " + query.attribute());
            };
            if (principal == null) {
                return Optional.empty();
            }
            if (principal.getUsername() == null || principal.getUsername().isEmpty()) {
                throw new DirectoryUnavailableException("LDAP result has no username");
            }
            return Optional.of(new DirectoryEntry(key(principal.getUsername(), principal.getDomain()), profile(principal)));
        } catch (IllegalArgumentException | DirectoryUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // LdapManager reports non-unique results as runtime exceptions for all three lookups.
            if (e.getMessage() != null && e.getMessage().startsWith("LDAP error, non unique result")) {
                throw new DirectoryAmbiguousException("Ambiguous LDAP directory lookup", e);
            }
            throw new DirectoryUnavailableException("LDAP directory lookup failed", e);
        }
    }

    @Override
    public Optional<UserProfile> get(ExternalIdentityKey identity) {
        if (!acceptsIdentity(identity)) {
            throw new IllegalArgumentException("Invalid LDAP identity");
        }
        try {
            JsonNode value = JSON.readTree(identity.subject());
            return find(new DirectoryQuery("username", value.get(0).textValue(),
                    value.get(1).isNull() ? null : value.get(1).textValue()))
                    .filter(entry -> entry.identity().equals(identity)).map(DirectoryEntry::profile);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid LDAP identity", e);
        }
    }

    public static ExternalIdentityKey key(String username, String domain) {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("LDAP username must not be empty");
        }
        try {
            String subject = JSON.writeValueAsString(Arrays.asList(username.toLowerCase(Locale.ROOT),
                    domain == null ? null : domain.toLowerCase(Locale.ROOT)));
            return new ExternalIdentityKey("ldap", "", subject);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid LDAP identity", e);
        }
    }

    private static UserProfile profile(LdapPrincipal p) {
        // LDAP attributes may contain credentials and are not needed to identify a profile.
        return new UserProfile(p.getUsername(), p.getDomain(), p.getDisplayName(), p.getEmail(), Map.of());
    }

}
