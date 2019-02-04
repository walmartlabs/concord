package com.walmartlabs.concord.server.security.apikey;

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

import org.apache.shiro.authc.RememberMeAuthenticationToken;

import java.util.UUID;

public class ApiKey implements RememberMeAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String key;
    private final boolean rememberMe;

    public ApiKey(UUID userId, String key, boolean rememberMe) {
        this.userId = userId;
        this.key = key;
        this.rememberMe = rememberMe;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getKey() {
        return key;
    }

    @Override
    public Object getPrincipal() {
        return getUserId();
    }

    @Override
    public Object getCredentials() {
        return getKey();
    }

    @Override
    public boolean isRememberMe() {
        return rememberMe;
    }

    @Override
    public String toString() {
        return "ApiKey{" +
                "userId=" + userId +
                ", key='" + key + '\'' +
                ", rememberMe=" + rememberMe +
                '}';
    }
}
