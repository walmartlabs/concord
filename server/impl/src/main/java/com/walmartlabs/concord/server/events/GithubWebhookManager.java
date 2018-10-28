package com.walmartlabs.concord.server.events;

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

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Named
public class GithubWebhookManager {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookManager.class);

    private static final Set<GHEvent> EVENTS = ImmutableSet.of(GHEvent.PUSH, GHEvent.PULL_REQUEST);

    private final Supplier<GitHub> client;
    private final GithubConfiguration cfg;

    @Inject
    public GithubWebhookManager(GithubConfiguration cfg) {
        this.cfg = cfg;
        this.client = Suppliers.memoize(this::connect);
    }

    public Long register(String githubRepoName) {
        if (client.get() == null) {
            log.warn("register ['{}'] -> not configured, ignored", githubRepoName);
            return null;
        }

        try {
            GHRepository repo = client.get().getRepository(githubRepoName);

            final Map<String, String> config = new HashMap<>();
            config.put("url", cfg.getWebhookUrl());
            config.put("content_type", "json");
            config.put("secret", cfg.getSecret());

            GHHook hook = repo.createHook("web", config, EVENTS, true);

            log.info("register ['{}'] -> ok (id: {})", githubRepoName, hook.getId());
            return hook.getId();
        } catch (GHFileNotFoundException e) {
            log.warn("register ['{}'] -> repository not found", githubRepoName);
            return null;
        } catch (IOException e) {
            log.error("register ['{}'] -> error", githubRepoName, e);
            return null;
        }
    }

    public void unregister(String githubRepoName) {
        if (client.get() == null) {
            log.warn("unregister ['{}'] -> not configured, ignored", githubRepoName);
            return;
        }

        try {
            GHRepository repo = client.get().getRepository(githubRepoName);

            List<GHHook> hooks = repo.getHooks().stream()
                    .filter(h -> h.getConfig() != null)
                    .filter(h -> h.getConfig().get("url") != null)
                    .filter(h -> h.getConfig().get("url").startsWith(cfg.getWebhookUrl()))
                    .collect(Collectors.toList());

            hooks.forEach(this::deleteWebhook);

            log.info("unregister ['{}'] -> ok", githubRepoName);
        } catch (GHFileNotFoundException e) {
            log.warn("unregister ['{}'] -> webhook not found", githubRepoName);
        } catch (IOException e) {
            log.error("unregister ['{}'] -> error: {}", githubRepoName, e.getMessage());
        }
    }

    private GitHub connect() {
        if (!cfg.isWebhookRegistrationEnabled()) {
            throw new IllegalStateException("Webhook registration is disabled, won't create a GitHub API client");
        }

        log.info("init -> connecting to the GitHub API '{}'", cfg.getApiUrl());
        try {
            return GitHub.connectToEnterprise(cfg.getApiUrl(), cfg.getOauthAccessToken());
        } catch (IOException e) {
            log.error("error -> connecting to the GitHub API '{}'", cfg.getApiUrl());
            throw new RuntimeException(e);
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
