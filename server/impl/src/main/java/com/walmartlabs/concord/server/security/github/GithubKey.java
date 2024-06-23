package com.walmartlabs.concord.server.security.github;

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

import com.walmartlabs.concord.server.security.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;

import java.util.UUID;

public class GithubKey implements AuthenticationToken {

    public static GithubKey getCurrent() {
        return SecurityUtils.getCurrent(GithubKey.class);
    }

    private static final long serialVersionUID = 1L;

    private final String key;
    private final UUID projectId;
    private final String repoToken;

    public GithubKey(String key, UUID projectId, String repoToken) {
        this.key = key;
        this.projectId = projectId;
        this.repoToken = repoToken;
    }

    public String getKey() {
        return key;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getRepoToken() {
        return repoToken;
    }

    @Override
    public Object getPrincipal() {
        return getKey();
    }

    @Override
    public Object getCredentials() {
        return key != null ? key : repoToken;
    }
}
