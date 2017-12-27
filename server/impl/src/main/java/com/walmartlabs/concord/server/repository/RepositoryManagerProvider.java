package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;
import com.walmartlabs.concord.server.org.project.ProjectDao;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import static com.walmartlabs.concord.server.repository.CachedRepositoryManager.RepositoryCacheDao;

@Singleton
@Named
public class RepositoryManagerProvider implements Provider<RepositoryManager> {

    private final RepositoryConfiguration cfg;
    private final RepositoryMetaManager repositoryMetaManager;
    private final RepositoryCacheDao repositoryDao;
    private final GithubConfiguration githubConfiguration;
    private final GithubRepositoryProvider githubRepositoryProvider;
    private final ClasspathRepositoryProvider classpathRepositoryProvider;
    private final ProjectDao projectDao;

    @Inject
    public RepositoryManagerProvider(RepositoryConfiguration cfg,
                                     RepositoryMetaManager repositoryMetaManager,
                                     RepositoryCacheDao repositoryDao,
                                     GithubConfiguration githubConfiguration,
                                     GithubRepositoryProvider githubRepositoryProvider,
                                     ClasspathRepositoryProvider classpathRepositoryProvider,
                                     ProjectDao projectDao) {
        this.cfg = cfg;
        this.repositoryMetaManager = repositoryMetaManager;
        this.repositoryDao = repositoryDao;
        this.githubConfiguration = githubConfiguration;
        this.githubRepositoryProvider = githubRepositoryProvider;
        this.classpathRepositoryProvider = classpathRepositoryProvider;
        this.projectDao = projectDao;
    }

    @Override
    public RepositoryManager get() {
        RepositoryManager rm = new RepositoryManagerImpl(cfg, githubRepositoryProvider, classpathRepositoryProvider, projectDao);
        if (githubConfiguration.isCacheEnabled()) {
            return new CachedRepositoryManager(repositoryMetaManager, rm, repositoryDao);
        } else {
            return rm;
        }
    }
}