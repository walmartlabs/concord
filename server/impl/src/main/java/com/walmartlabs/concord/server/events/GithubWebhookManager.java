package com.walmartlabs.concord.server.events;

import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import org.eclipse.sisu.EagerSingleton;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Named
public class GithubWebhookManager {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookManager.class);

    private static final Set<GHEvent> EVENTS = ImmutableSet.of(GHEvent.PUSH);

    private final GitHub gh;

    private final GithubConfiguration cfg;

    @Inject
    public GithubWebhookManager(GithubConfiguration cfg) throws IOException {
        this.cfg = cfg;
        if(cfg.getApiUrl() == null) {
            log.warn("github api url not configured");
            this.gh = null;
        } else {
            log.info("connecting to github api '{}'", cfg.getApiUrl());
            this.gh = GitHub.connectToEnterprise(cfg.getApiUrl(), cfg.getOauthAccessToken());
        }
    }

    public void register(String repoName, String webhookUrl) {
        try {
            if(gh == null) {
                log.warn("register ['{}', '{}'] -> not configured, ignored", repoName, webhookUrl);
                return;
            }
            GHRepository repo = gh.getRepository(repoName);

            final Map<String, String> config = new HashMap<>();
            config.put("url", webhookUrl);
            config.put("content_type", "json");
            config.put("secret", cfg.getSecret());

            repo.createHook("web", config, EVENTS, true);

            log.info("register ['{}', '{}'] -> ok", repoName, webhookUrl);
        } catch (IOException e) {
            log.error("register ['{}', '{}'] -> error", repoName, webhookUrl, e);
            throw new RuntimeException("register webhook -> error", e);
        }
    }

    public void unregister(String repoName, String webhookUrl) {
        try {
            if(gh == null) {
                log.warn("unregister ['{}', '{}'] -> not configured, ignored", repoName, webhookUrl);
                return;
            }

            GHRepository repo = gh.getRepository(repoName);

            List<GHHook> hooks = repo.getHooks().stream()
                    .filter(h -> h.getConfig() != null)
                    .filter(h -> webhookUrl.equals(h.getConfig().get("url")))
                    .collect(Collectors.toList());

            hooks.forEach(this::deleteWebhook);

            log.info("unregister ['{}', '{}'] -> ok", repoName, webhookUrl);
        } catch (IOException e) {
            log.error("unregister ['{}', '{}'] -> error", repoName, webhookUrl, e);
        }
    }

    private void deleteWebhook(GHHook hook) {
        try {
            hook.delete();
            log.info("deleteWebhook ['{}'] -> ok", hook);
        } catch (IOException e) {
            log.error("deleteWebhook ['{}'] -> error", hook, e);
        }
    }
}
