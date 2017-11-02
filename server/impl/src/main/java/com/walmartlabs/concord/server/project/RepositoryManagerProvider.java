package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import static com.walmartlabs.concord.server.project.CachedRepositoryManager.RepositoryCacheDao;

@Singleton
@Named
public class RepositoryManagerProvider implements Provider<RepositoryManager> {

    private final RepositoryMetaManager repositoryMetaManager;
    private final RepositoryCacheDao repositoryDao;
    private final RepositoryConfiguration repositoryConfiguration;
    private final GithubConfiguration githubConfiguration;

    @Inject
    public RepositoryManagerProvider(RepositoryMetaManager repositoryMetaManager,
                                     RepositoryCacheDao repositoryDao,
                                     RepositoryConfiguration repositoryConfiguration,
                                     GithubConfiguration githubConfiguration) {
        this.repositoryMetaManager = repositoryMetaManager;
        this.repositoryDao = repositoryDao;
        this.repositoryConfiguration = repositoryConfiguration;
        this.githubConfiguration = githubConfiguration;
    }

    @Override
    public RepositoryManager get() {
        RepositoryManager rm = new RepositoryManagerImpl(repositoryConfiguration);
        if (githubConfiguration.getApiUrl() == null) {
            return rm;
        } else {
            return new CachedRepositoryManager(repositoryMetaManager, rm, repositoryDao);
        }
    }
}