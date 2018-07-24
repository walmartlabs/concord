package com.walmartlabs.concord.server.repository;

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

import com.walmartlabs.concord.server.cfg.GitConfiguration;
import com.walmartlabs.concord.server.org.secret.SecretManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
@Named
public class RepositoryDataProvider implements Provider<RepositoryProvider> {

    private final GitConfiguration cfg;
    private final SecretManager secretManager;

    @Inject
    public RepositoryDataProvider(GitConfiguration githubConfiguration,
                                  SecretManager secretManager) {
        this.cfg = githubConfiguration;
        this.secretManager = secretManager;
    }

    @Override
    public RepositoryProvider get() {
        if (cfg.isUseJGit()) {
            return new JGitRepositoryProvider(secretManager);
        } else {
            return new GitCliRepositoryProvider(secretManager, cfg);
        }
    }
}
