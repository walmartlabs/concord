package com.walmartlabs.concord.server.security.github;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

public class GithubKey implements AuthenticationToken {

    private final String key;

    public GithubKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public Object getPrincipal() {
        return getKey();
    }

    @Override
    public Object getCredentials() {
        return getKey();
    }

    @Override
    public String toString() {
        return "GithubKey{" +
                "key='" + key + '\'' +
                '}';
    }
}
