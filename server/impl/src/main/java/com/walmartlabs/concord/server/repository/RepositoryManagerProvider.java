package com.walmartlabs.concord.server.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
