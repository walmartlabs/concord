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

import com.walmartlabs.ollie.config.Config;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Named
@Singleton
public class LdapConfiguration implements Serializable {

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
    @Config("ldap.userSearchFilter")
    private String userSearchFilter;

    @Inject
    @Config("ldap.usernameProperty")
    private String usernameProperty;

    @Inject
    @Config("ldap.systemUsername")
    private String systemUsername;

    @Inject
    @Config("ldap.systemPassword")
    private String systemPassword;

    private final Set<String> exposeAttributes;

    @Inject
    public LdapConfiguration(@Config("ldap.exposeAttributes") @Nullable String exposeAttributes) {
        this.exposeAttributes = split(exposeAttributes);
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

    public String getUserSearchFilter() {
        return userSearchFilter;
    }

    public String getUsernameProperty() {
        return usernameProperty;
    }

    public String getSystemUsername() {
        return systemUsername;
    }

    public String getSystemPassword() {
        return systemPassword;
    }

    public Set<String> getExposeAttributes() {
        return exposeAttributes;
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
