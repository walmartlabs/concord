package com.walmartlabs.concord.server.cfg;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

@Named
@Singleton
public class GithubConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GithubConfiguration.class);

    private static final String CFG_KEY = "GITHUB_CFG";
    private static final long DEFAULT_REFRESH_INTERVAL = 60000;

    private final String secret;
    private final String apiUrl;
    private final String oauthAccessToken;
    private final String webhookUrl;
    private final String githubUrl;
    private final long refreshInterval;

    public GithubConfiguration() throws IOException {
        Properties props = new Properties();

        String path = System.getenv(CFG_KEY);
        if (path != null) {
            try (InputStream in = Files.newInputStream(Paths.get(path))) {
                props.load(in);
            }

            log.info("init -> using external github configuration: {}", path);

            this.secret = props.getProperty("secret");
            this.apiUrl = props.getProperty("apiUrl");
            this.oauthAccessToken = props.getProperty("oauthAccessToken");
            this.webhookUrl = props.getProperty("webhookUrl");
            this.githubUrl = props.getProperty("githubUrl");
            this.refreshInterval = getLong(props, "refreshInterval", DEFAULT_REFRESH_INTERVAL);
        } else {
            this.secret = "123qwe";
            this.apiUrl = null;
            this.oauthAccessToken = null;
            this.webhookUrl = "";
            this.githubUrl = "";
            this.refreshInterval = DEFAULT_REFRESH_INTERVAL;

            log.warn("init -> no github configuration");
        }
    }

    public String getSecret() {
        return secret;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getOauthAccessToken() {
        return oauthAccessToken;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public long getRefreshInterval() {
        return refreshInterval;
    }

    private static long getLong(Properties props, String key, long defaultValue) {
        String s = props.getProperty(key);
        if (s == null) {
            return defaultValue;
        }
        return Long.parseLong(s);
    }
}
