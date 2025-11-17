package com.walmartlabs.concord.github.appinstallation;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.common.cfg.MappingAuthConfig;

import java.util.regex.Pattern;

public record GitHubAppAuthConfig(String apiUrl,
                                  String clientId,
                                  String privateKey,
                                  String username,
                                  Pattern urlPattern) implements MappingAuthConfig {

    public GitHubAppAuthConfig(String apiUrl, String clientId, String privateKey, String username, Pattern urlPattern) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must be provided");
        }

        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalArgumentException("privateKey must be provided");
        }

        // sanity check url pattern before this object gets too far out there
        if (!urlPattern.toString().contains("?<baseUrl>")) {
            throw new IllegalArgumentException("The url pattern must contain the ?<baseUrl> named group");
        }

        this.apiUrl = (apiUrl == null) ? "https://api.github.com" : apiUrl;
        this.clientId = clientId;
        this.privateKey = privateKey;
        this.username = username;
        this.urlPattern = urlPattern;
    }
}
