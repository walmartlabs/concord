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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.BackgroundTask;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static org.jooq.impl.DSL.value;

@Named
@Singleton
public class GithubWebhookService implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);

    private static final long RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(10);

    private final GithubConfiguration cfg;
    private final GithubWebhookManager webhookManager;
    private final RefresherDao refresherDao;

    private Thread worker;

    @Inject
    public GithubWebhookService(GithubConfiguration cfg,
                                GithubWebhookManager webhookManager,
                                RefresherDao refresherDao) {
        this.cfg = cfg;
        this.webhookManager = webhookManager;
        this.refresherDao = refresherDao;
    }

    @Override
    public void start() {
        if (!this.cfg.isEnabled() || this.cfg.getRefreshInterval() <= 0) {
            log.info("init -> the webhook refresh is disabled");
            return;
        }

        HookRefresher w = new HookRefresher(refresherDao);

        this.worker = new Thread(w, "webhook-refresher");
        this.worker.start();
    }

    @Override
    public void stop() {
        if (this.worker != null) {
            this.worker.interrupt();
        }
    }

    public boolean register(UUID projectId, String repoName, String repoUrl) {
        if (!needWebhookForRepository(repoUrl)) {
            log.info("register ['{}', '{}', '{}'] -> not a GitHub URL", projectId, repoName, repoUrl);
            return false;
        }

        String githubRepoName = GithubUtils.getRepositoryName(repoUrl);

        Long id = webhookManager.register(githubRepoName);

        log.info("register ['{}', '{}', '{}'] -> {} (git repo: '{}')",
                projectId, repoName, repoUrl, id, githubRepoName);

        return id != null;
    }

    private void unregister(UUID projectId, String repoName, String repoUrl) {
        if (!needWebhookForRepository(repoUrl)) {
            log.info("unregister ['{}', '{}', '{}'] -> not a GitHub URL", projectId, repoName, repoUrl);
            return;
        }

        String githubRepoName = GithubUtils.getRepositoryName(repoUrl);

        webhookManager.unregister(githubRepoName);

        log.info("unregister ['{}', '{}', '{}'] -> ok (git repo: '{}')",
                projectId, repoName, repoUrl, githubRepoName);
    }

    private boolean needWebhookForRepository(String repoUrl) {
        return repoUrl.contains(cfg.getGithubDomain());
    }

    private class HookRefresher implements Runnable {

        private final RefresherDao dao;

        private HookRefresher(RefresherDao dao) {
            this.dao = dao;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    List<Entity> repositories = dao.list();
                    repositories.forEach(r -> refreshWebhook(r.getProjectId(), r.getRepoId(), r.getRepoName(), r.getRepoUrl()));
                    log.info("run -> {} repositories processed", repositories.size());

                    sleep(cfg.getRefreshInterval());
                } catch (Exception e) {
                    log.warn("run -> hook refresh error: {}. Will retry in {}ms...", e.getMessage(), RETRY_INTERVAL);
                    sleep(RETRY_INTERVAL);
                }
            }
        }

        private void refreshWebhook(UUID projectId, UUID repoId, String repoName, String repoUrl) {
            try {
                unregister(projectId, repoName, repoUrl);
                boolean success = register(projectId, repoName, repoUrl);
                dao.update(repoId, success);
            } catch (Exception e) {
                log.warn("refreshWebhook ['{}', '{}', '{}'] -> failed: {}", projectId, repoName, repoUrl, e.getMessage());
            }
        }

        private void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().isInterrupted();
            }
        }
    }

    @Named
    private static class RefresherDao extends AbstractDao {

        @Inject
        protected RefresherDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        public List<Entity> list() {
            try (DSLContext tx = DSL.using(cfg)) {
                return tx.select(REPOSITORIES.PROJECT_ID, REPOSITORIES.REPO_ID, REPOSITORIES.REPO_NAME, REPOSITORIES.REPO_URL)
                        .from(REPOSITORIES)
                        .fetch(RefresherDao::toEntry);
            }
        }

        public void update(UUID repoId, boolean hasWebHook) {
            tx(tx ->
                    tx.update(REPOSITORIES)
                            .set(REPOSITORIES.HAS_WEBHOOK, value(hasWebHook))
                            .where(REPOSITORIES.REPO_ID.eq(repoId))
                            .execute());
        }

        private static Entity toEntry(Record4<UUID, UUID, String, String> r) {
            return new Entity(r.get(REPOSITORIES.PROJECT_ID),
                    r.get(REPOSITORIES.REPO_ID),
                    r.get(REPOSITORIES.REPO_NAME),
                    r.get(REPOSITORIES.REPO_URL));
        }
    }

    private static class Entity {

        private final UUID projectId;
        private final UUID repoId;
        private final String repoName;
        private final String repoUrl;

        private Entity(UUID projectId, UUID repoId, String repoName, String repoUrl) {
            this.projectId = projectId;
            this.repoId = repoId;
            this.repoName = repoName;
            this.repoUrl = repoUrl;
        }

        public UUID getProjectId() {
            return projectId;
        }

        public UUID getRepoId() {
            return repoId;
        }

        public String getRepoName() {
            return repoName;
        }

        public String getRepoUrl() {
            return repoUrl;
        }
    }
}
