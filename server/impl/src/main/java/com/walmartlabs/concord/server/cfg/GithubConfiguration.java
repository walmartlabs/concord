package com.walmartlabs.concord.server.cfg;

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

    private final String secret;
    private final String apiUrl;
    private final String oauthAccessToken;
    private final String webhookUrl;
    private final String githubUrl;

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
        } else {
            this.secret = "";
            this.apiUrl = null;
            this.oauthAccessToken = null;
            this.webhookUrl = "";
            this.githubUrl = "";

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
}
