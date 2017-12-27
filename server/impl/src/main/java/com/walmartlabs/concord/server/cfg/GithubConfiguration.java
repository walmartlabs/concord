package com.walmartlabs.concord.server.cfg;

import com.walmartlabs.concord.common.ConfigurationUtils;
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

    private final boolean enabled;
    private final String secret;
    private final String apiUrl;
    private final String oauthAccessToken;
    private final String webhookUrl;
    private final String githubUrl;
    private final long refreshInterval;
    private final boolean cacheEnabled;

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
            this.refreshInterval = Utils.getLong(props, "refreshInterval", DEFAULT_REFRESH_INTERVAL);
            this.cacheEnabled = Utils.getBoolean(props, "", false);

            this.enabled = true;
        } else {
            this.secret = null;
            this.apiUrl = null;
            this.oauthAccessToken = null;
            this.webhookUrl = null;
            this.githubUrl = "";
            this.refreshInterval = DEFAULT_REFRESH_INTERVAL;
            this.cacheEnabled = false;

            this.enabled = false;

            log.warn("init -> no github configuration");
        }
    }

    public boolean isEnabled() {
        return enabled;
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

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
}
