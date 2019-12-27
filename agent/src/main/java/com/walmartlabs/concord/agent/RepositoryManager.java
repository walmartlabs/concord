package com.walmartlabs.concord.agent;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.agent.cfg.GitConfiguration;
import com.walmartlabs.concord.agent.cfg.RepositoryCacheConfiguration;
import com.walmartlabs.concord.client.SecretClient;
import com.walmartlabs.concord.imports.Import.SecretDefinition;
import com.walmartlabs.concord.repository.*;
import com.walmartlabs.concord.sdk.Secret;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Named
@Singleton
public class RepositoryManager {

    private final SecretClient secretClient;
    private final RepositoryProviders providers;
    private final RepositoryCache repositoryCache;

    @Inject
    public RepositoryManager(SecretClient secretClient,
                             GitConfiguration gitCfg,
                             RepositoryCacheConfiguration cacheCfg,
                             ObjectMapper objectMapper) throws IOException {

        this.secretClient = secretClient;

        GitClientConfiguration clientCfg = GitClientConfiguration.builder()
                .oauthToken(gitCfg.getToken())
                .shallowClone(gitCfg.isShallowClone())
                .httpLowSpeedLimit(gitCfg.getHttpLowSpeedLimit())
                .httpLowSpeedTime(gitCfg.getHttpLowSpeedTime())
                .sshTimeout(gitCfg.getSshTimeout())
                .sshTimeoutRetryCount(gitCfg.getSshTimeoutRetryCount())
                .build();

        List<RepositoryProvider> providers = Collections.singletonList(new GitCliRepositoryProvider(clientCfg));
        this.providers = new RepositoryProviders(providers);

        this.repositoryCache = new RepositoryCache(cacheCfg.getCacheDir(),
                cacheCfg.getInfoDir(),
                cacheCfg.getLockTimeout(),
                cacheCfg.getMaxAge(),
                objectMapper);
    }

    public void export(String repoUrl, String commitId, String repoPath, Path dest, SecretDefinition secretDefinition) throws ExecutionException {
        export(repoUrl, null, commitId, repoPath, dest, secretDefinition, Collections.emptyList());
    }

    public void export(String repoUrl, String branch, String commitId, String repoPath, Path dest, SecretDefinition secretDefinition, List<String> ignorePatterns) throws ExecutionException {
        Secret secret = getSecret(secretDefinition);

        Path cacheDir = repositoryCache.getPath(repoUrl);

        repositoryCache.withLock(repoUrl, () -> {
            Repository repo = providers.fetch(repoUrl, branch, commitId, repoPath, secret, cacheDir);
            repo.export(dest, ignorePatterns);
            return null;
        });
    }

    private Secret getSecret(SecretDefinition secret) throws ExecutionException {
        if (secret == null) {
            return null;
        }

        try {
            return secretClient.getData(secret.org(), secret.name(), secret.password(), null);
        } catch (Exception e) {
            throw new ExecutionException("Error while retrieving a secret '" + secret.name() + "' in org '" + secret.org() + "': " + e.getMessage(), e);
        }
    }
}
