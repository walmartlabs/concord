package com.walmartlabs.concord.server.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.config.Config;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LdapConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Config("ldap.url")
    private String url;

    @Inject
    @Config("ldap.searchBase")
    private String searchBase;

    @Inject
    @Config("ldap.principalSearchFilter")
    private String principalSearchFilter;

    @Inject
    @Config("ldap.groupSearchFilter")
    private String groupSearchFilter;

    @Inject
    @Config("ldap.groupNameProperty")
    private String groupNameProperty;

    @Inject
    @Config("ldap.groupDisplayNameProperty")
    private String groupDisplayNameProperty;

    @Inject
    @Config("ldap.systemUsername")
    private String systemUsername;

    @Inject
    @Config("ldap.systemPassword")
    @Nullable
    private String systemPassword;

    @Inject
    @Config("ldap.userPrincipalNameProperty")
    private String userPrincipalNameProperty;

    @Inject
    @Config("ldap.usernameProperty")
    private String usernameProperty;

    @Inject
    @Config("ldap.mailProperty")
    private String mailProperty;

    @Inject
    @Config("ldap.autoCreateUsers")
    private boolean autoCreateUsers;

    @Inject
    @Nullable
    @Config("ldap.returningAttributes")
    private List<String> returningAttributes;

    @Inject
    @Nullable
    @Config("ldap.cacheDuration")
    private Duration cacheDuration;

    @Inject
    @Config("ldap.connectTimeout")
    private Duration connectTimeout;

    @Inject
    @Config("ldap.readTimeout")
    private Duration readTimeout;

    @Inject
    @Nullable
    @Config("ldap.dnsSRVName")
    private String dnsSRVName;

    private final Set<String> exposeAttributes;

    private final Set<String> excludeAttributes;

    @Inject
    public LdapConfiguration(@Config("ldap.exposeAttributes") @Nullable String exposeAttributes,
                             @Config("ldap.excludeAttributes") @Nullable String excludeAttributes) {

        this.exposeAttributes = split(exposeAttributes);
        this.excludeAttributes = split(excludeAttributes);
    }

    public String getUrl() {
        return url;
    }

    public String getSearchBase() {
        return searchBase;
    }

    public String getPrincipalSearchFilter() {
        return principalSearchFilter;
    }

    public String getGroupSearchFilter() {
        return groupSearchFilter;
    }

    public String getGroupNameProperty() {
        return groupNameProperty;
    }

    public String getGroupDisplayNameProperty() {
        return groupDisplayNameProperty;
    }

    public String getSystemUsername() {
        return systemUsername;
    }

    public String getSystemPassword() {
        return systemPassword;
    }

    public String getUserPrincipalNameProperty() {
        return userPrincipalNameProperty;
    }

    public String getUsernameProperty() {
        return usernameProperty;
    }

    public String getMailProperty() {
        return mailProperty;
    }

    public Set<String> getExposeAttributes() {
        return exposeAttributes;
    }

    public Set<String> getExcludeAttributes() {
        return excludeAttributes;
    }

    public List<String> getReturningAttributes() {
        return returningAttributes;
    }

    public boolean isAutoCreateUsers() {
        return autoCreateUsers;
    }

    public Duration getCacheDuration() {
        return cacheDuration;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public String getDnsSRVName() {
        return dnsSRVName;
    }

    private static Set<String> split(String s) {
        if (s == null || s.isEmpty()) {
            return Collections.emptySet();
        }

        s = s.trim();
        if (s.isEmpty()) {
            return Collections.emptySet();
        }

        String[] as = s.split(",");
        Set<String> result = new HashSet<>(as.length);
        Collections.addAll(result, s.split(","));

        return Collections.unmodifiableSet(result);
    }
}