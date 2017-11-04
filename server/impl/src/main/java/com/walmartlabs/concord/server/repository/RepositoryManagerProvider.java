package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;

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

    @Inject
    public RepositoryManagerProvider(RepositoryConfiguration cfg,
                                     RepositoryMetaManager repositoryMetaManager,
                                     RepositoryCacheDao repositoryDao,
                                     GithubConfiguration githubConfiguration,
                                     GithubRepositoryProvider githubRepositoryProvider, ClasspathRepositoryProvider classpathRepositoryProvider) {
        this.cfg = cfg;
        this.repositoryMetaManager = repositoryMetaManager;
        this.repositoryDao = repositoryDao;
        this.githubConfiguration = githubConfiguration;
        this.githubRepositoryProvider = githubRepositoryProvider;
        this.classpathRepositoryProvider = classpathRepositoryProvider;
    }

    @Override
    public RepositoryManager get() {
        RepositoryManager rm = new RepositoryManagerImpl(cfg, githubRepositoryProvider, classpathRepositoryProvider);
        if (githubConfiguration.getApiUrl() == null) {
            return rm;
        } else {
            return new CachedRepositoryManager(repositoryMetaManager, rm, repositoryDao);
        }
    }
}