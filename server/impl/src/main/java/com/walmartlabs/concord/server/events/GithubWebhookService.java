package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.project.RepositoryDao;
import org.eclipse.sisu.EagerSingleton;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;

@Named
@EagerSingleton
public class GithubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);

    private static final long REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(5);
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
        if (this.cfg.getApiUrl() == null) {
            log.info("init -> webhook refresh disabled");
            return;
        }

        HookRefresher w = new HookRefresher(dao);

        Thread t = new Thread(w, "webhook-refresher");
        t.start();
    }

    public void register(String projectName, String repositoryName, String repoUrl) {
        if(!needWebhookForRepository(repoUrl)) {
            log.info("register ['{}', '{}', '{}'] -> not github url", projectName, repositoryName, repoUrl);
            return;
        }

        String webhookUrl = createWebHookUrl(projectName, repositoryName);
        String githubRepoName = GithubUtils.getRepositoryName(repoUrl);

        webhookManager.register(githubRepoName, webhookUrl);

        log.info("register ['{}', '{}', '{}'] -> ok (git repo: '{}')",
                projectName, repositoryName, repoUrl, githubRepoName);
    }

    public void unregister(String projectName) {
        repositoryDao.list(projectName).forEach(re -> unregister(projectName, re.getName(), re.getUrl()));
        log.info("unregister ['{}'] -> ok", projectName);
    }

    public void unregister(String projectName, String repositoryName) {
        RepositoryEntry re = repositoryDao.get(projectName, repositoryName);

        unregister(projectName, repositoryName, re.getUrl());

        log.info("unregister ['{}', '{}'] -> ok", projectName, repositoryName);
    }

    private void unregister(String projectName, String repositoryName, String repoUrl) {
        if(!needWebhookForRepository(repoUrl)) {
            log.info("unregister ['{}', '{}', '{}'] -> not github url", projectName, repositoryName, repoUrl);
            return;
        }

        String webhookUrl = createWebHookUrl(projectName, repositoryName);
        String githubRepoName = GithubUtils.getRepositoryName(repoUrl);

        webhookManager.unregister(githubRepoName, webhookUrl);

        log.info("unregister ['{}', '{}', '{}'] -> ok (git repo: '{}')",
                projectName, repositoryName, repoUrl, githubRepoName);
    }

    private boolean needWebhookForRepository(String repoUrl) {
        return repoUrl.contains(cfg.getGithubUrl());
    }

    private String createWebHookUrl(String projectName, String repositoryName) {
        return cfg.getWebhookUrl() + "/" + projectName + "/" + repositoryName;
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

                    repositories.forEach(r -> {
                        refreshWebhook(r.getProjectName(), r.getRepoName(), r.getRepoUrl());
                    });

                    log.info("run -> {} repositories processed", repositories.size());

                    sleep(REFRESH_INTERVAL);
                } catch (Exception e) {
                    log.warn("run -> hook refresh error: {}. Will retry in {}ms...", e.getMessage(), RETRY_INTERVAL);
                    sleep(RETRY_INTERVAL);
                }
            }
        }

        private void refreshWebhook(String projectName, String repositoryName, String repoUrl) {
            unregister(projectName, repositoryName, repoUrl);
            register(projectName, repositoryName, repoUrl);
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
                return tx.select(REPOSITORIES.PROJECT_NAME, REPOSITORIES.REPO_NAME, REPOSITORIES.REPO_URL)
                        .from(REPOSITORIES)
                        .fetch(RefresherDao::toEntry);
            }
        }

        private static Entity toEntry(Record3<String, String, String> r) {
            return new Entity(r.get(REPOSITORIES.PROJECT_NAME),
                    r.get(REPOSITORIES.REPO_NAME),
                    r.get(REPOSITORIES.REPO_URL));
        }
    }

    private static class Entity {
        private final String projectName;
        private final String repoName;
        private final String repoUrl;

        private Entity(String projectName, String repoName, String repoUrl) {
            this.projectName = projectName;
            this.repoName = repoName;
            this.repoUrl = repoUrl;
        }

        public String getProjectName() {
            return projectName;
        }

        public String getRepoName() {
            return repoName;
        }

        public String getRepoUrl() {
            return repoUrl;
        }
    }
}
