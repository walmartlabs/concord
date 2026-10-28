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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.cfg.LdapGroupSyncConfiguration;
import com.walmartlabs.concord.server.sdk.security.AuthorizationData;
import com.walmartlabs.concord.server.sdk.security.AuthorizationProvider;
import com.walmartlabs.concord.server.sdk.security.AuthorizationRequest;
import com.walmartlabs.concord.server.sdk.security.AuthorizationSubjectNotFoundException;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Set;

/** LDAP membership observations are independent of LDAP password authentication. */
public class LdapAuthorizationProvider implements AuthorizationProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LdapConfiguration configuration;
    private final LdapGroupSyncConfiguration syncConfiguration;
    private final LdapManager manager;
    private final LdapUserDirectory directory;

    @Inject
    public LdapAuthorizationProvider(LdapConfiguration configuration, LdapGroupSyncConfiguration syncConfiguration,
                                     LdapManager manager, LdapUserDirectory directory) {
        this.configuration = configuration;
        this.syncConfiguration = syncConfiguration;
        this.manager = manager;
        this.directory = directory;
    }

    @Override
    public String id() {
        return "ldap";
    }

    @Override
    public boolean isActive() {
        return configured(configuration.getUrl()) || configured(configuration.getDnsSRVName());
    }

    private static boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public boolean acceptsIdentity(ExternalIdentityKey identity) {
        return directory.acceptsIdentity(identity);
    }

    @Override
    public Duration defaultMaxAge() {
        return Duration.ofDays(1);
    }

    @Override
    public Duration defaultLoginMaxAge() {
        return syncConfiguration.getMinAgeLogin();
    }

    @Override
    public AuthorizationData resolve(AuthorizationRequest request) {
        if (!isActive() || !acceptsIdentity(request.identity())) {
            throw new AuthorizationUnavailableException("LDAP source is inactive or identity is invalid");
        }
        try {
            JsonNode subject = JSON.readTree(request.identity().subject());
            Set<String> groups = manager.getGroups(subject.get(0).textValue(),
                    subject.get(1).isNull() ? null : subject.get(1).textValue());
            if (groups == null) {
                throw new AuthorizationSubjectNotFoundException("LDAP subject not found");
            }
            return new AuthorizationData(groups, Set.of(), Set.of(), null);
        } catch (AuthorizationSubjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthorizationUnavailableException("LDAP group lookup failed", e);
        }
    }
}
