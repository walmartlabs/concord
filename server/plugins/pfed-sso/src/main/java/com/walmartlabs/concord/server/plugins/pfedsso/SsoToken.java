package com.walmartlabs.concord.server.plugins.pfedsso;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import org.apache.shiro.authc.AuthenticationToken;

public class SsoToken implements AuthenticationToken {

    private final String username;
    private final String domain;
    private final SsoClient.Profile profile;

    public SsoToken(String username, String domain, SsoClient.Profile profile) {
        this.username = username;
        this.domain = domain;
        this.profile = profile;
    }

    public String getUsername() {
        return username;
    }

    public String getDomain() {
        return domain;
    }

    public SsoClient.Profile getProfile() {
        return profile;
    }

    @Override
    public Object getPrincipal() {
        return username + "@" + domain;
    }

    @Override
    public Object getCredentials() {
        return getPrincipal();
    }

    @Override
    public String toString() {
        return "SsoToken{" +
                "username='" + username + '\'' +
                ", domain='" + domain + '\'' +
                ", profile=" + profile +
                '}';
    }
}
