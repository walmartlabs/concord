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

import org.apache.shiro.authc.AuthenticationToken;
import org.pac4j.oidc.profile.OidcProfile;

import java.io.Serializable;

public class OidcToken implements AuthenticationToken, Serializable {

    private static final long serialVersionUID = 1L;

    private final OidcProfile profile;

    public OidcToken(OidcProfile profile) {
        this.profile = profile;
    }

    public OidcProfile getProfile() {
        return profile;
    }

    @Override
    public Object getPrincipal() {
        return profile.getId();
    }

    @Override
    public Object getCredentials() {
        return profile;
    }

    @Override
    public String toString() {
        return "OidcToken{" +
                "profile=" + profile +
                '}';
    }
}
