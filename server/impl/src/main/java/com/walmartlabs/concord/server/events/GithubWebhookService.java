package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.project.RepositoryDao;
import org.eclipse.sisu.EagerSingleton;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;

@Named
@EagerSingleton
public class GithubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);

    private static final long RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(10);

    private final RepositoryDao repositoryDao;
    private final GithubConfiguration cfg;
    private final GithubWebhookManager webhookManager;

    @Inject
    public GithubWebhookService(RepositoryDao repositoryDao,
                                GithubConfiguration cfg,
                                GithubWebhookManager webhookManager,
                                RefresherDao refresherDao) {
        this.repositoryDao = repositoryDao;
        this.cfg = cfg;
        this.webhookManager = webhookManager;

        init(refresherDao);
    }

    private void init(RefresherDao dao) {
        if (this.cfg.getApiUrl() == null || this.cfg.getRefreshInterval() <= 0) {
            log.info("init -> webhook refresh disabled");
            return;
        }

        HookRefresher w = new HookRefresher(dao);

        Thread t = new Thread(w, "webhook-refresher");
        t.start();
    }

    public void register(UUID projectId, UUID repoId, String repoUrl) {
        if(!needWebhookForRepository(repoUrl)) {
            log.info("register ['{}', '{}', '{}'] -> not a GitHub url", projectId, repoId, repoUrl);
            return;
        }

        String webhookUrl = createWebHookUrl(projectId, repoId);
        String githubRepoName = GithubUtils.getRepositoryName(repoUrl);

        webhookManager.register(githubRepoName, webhookUrl);

        log.info("register ['{}', '{}', '{}'] -> ok (git repo: '{}')",
                projectId, repoId, repoUrl, githubRepoName);
    }

    public void unregister(UUID projectId) {
        repositoryDao.list(projectId).forEach(re -> unregister(projectId, re.getId(), re.getUrl()));
        log.info("unregister ['{}'] -> ok", projectId);
    }

    public void unregister(UUID projectId, UUID repoId) {
        RepositoryEntry re = repositoryDao.get(projectId, repoId);

        unregister(projectId, repoId, re.getUrl());

        log.info("unregister ['{}', '{}'] -> ok", projectId, repoId);
    }

    private void unregister(UUID projectId, UUID repoId, String repoUrl) {
        if(!needWebhookForRepository(repoUrl)) {
            log.info("unregister ['{}', '{}', '{}'] -> not a GitHub url", projectId, repoId, repoUrl);
            return;
        }

        String webhookUrl = createWebHookUrl(projectId, repoId);
        String githubRepoName = GithubUtils.getRepositoryName(repoUrl);

        webhookManager.unregister(githubRepoName, webhookUrl);

        log.info("unregister ['{}', '{}', '{}'] -> ok (git repo: '{}')",
                projectId, repoId, repoUrl, githubRepoName);
    }

    private boolean needWebhookForRepository(String repoUrl) {
        return repoUrl.contains(cfg.getGithubUrl());
    }

    private String createWebHookUrl(UUID projectId, UUID repoId) {
        return cfg.getWebhookUrl() + "/" + projectId + "/" + repoId;
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
                    repositories.forEach(r -> refreshWebhook(r.getProjectId(), r.getRepoId(), r.getRepoUrl()));
                    log.info("run -> {} repositories processed", repositories.size());

                    sleep(cfg.getRefreshInterval());
                } catch (Exception e) {
                    log.warn("run -> hook refresh error: {}. Will retry in {}ms...", e.getMessage(), RETRY_INTERVAL);
                    sleep(RETRY_INTERVAL);
                }
            }
        }

        private void refreshWebhook(UUID projectId, UUID repoId, String repoUrl) {
            unregister(projectId, repoId, repoUrl);
            register(projectId, repoId, repoUrl);
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
        protected RefresherDao(Configuration cfg) {
            super(cfg);
        }

        public List<Entity> list() {
            try (DSLContext tx = DSL.using(cfg)) {
                return tx.select(REPOSITORIES.PROJECT_ID, REPOSITORIES.REPO_ID, REPOSITORIES.REPO_URL)
                        .from(REPOSITORIES)
                        .fetch(RefresherDao::toEntry);
            }
        }

        private static Entity toEntry(Record3<UUID, UUID, String> r) {
            return new Entity(r.get(REPOSITORIES.PROJECT_ID),
                    r.get(REPOSITORIES.REPO_ID),
                    r.get(REPOSITORIES.REPO_URL));
        }
    }

    private static class Entity {

        private final UUID projectId;
        private final UUID repoId;
        private final String repoUrl;

        private Entity(UUID projectId, UUID repoId, String repoUrl) {
            this.projectId = projectId;
            this.repoId = repoId;
            this.repoUrl = repoUrl;
        }

        public UUID getProjectId() {
            return projectId;
        }

        public UUID getRepoId() {
            return repoId;
        }

        public String getRepoUrl() {
            return repoUrl;
        }
    }
}
