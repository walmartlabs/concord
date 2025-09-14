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
import com.walmartlabs.concord.agent.remote.ApiClientFactory;
import com.walmartlabs.concord.client2.SecretClient;
import com.walmartlabs.concord.imports.Import.SecretDefinition;
import com.walmartlabs.concord.repository.*;
import com.walmartlabs.concord.repository.auth.GitTokenProvider;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class RepositoryManager {

    private static final Logger log = LoggerFactory.getLogger(RepositoryManager.class);

    private final SecretClient secretClient;
    private final RepositoryProviders providers;
    private final RepositoryCache repositoryCache;
    private final GitConfiguration gitCfg;


    @Inject
    public RepositoryManager(SecretClient secretClient,
                             GitConfiguration gitCfg,
                             RepositoryCacheConfiguration cacheCfg,
                             ObjectMapper objectMapper,
                             DependencyManager dependencyManager,
                             ApiClientFactory apiClientFactory) throws IOException {

        this.secretClient = secretClient;
        this.gitCfg = gitCfg;

        GitClientConfiguration clientCfg = GitClientConfiguration.builder()
                .oauthToken(gitCfg.getToken())
                .defaultOperationTimeout(gitCfg.getDefaultOperationTimeout())
                .fetchTimeout(gitCfg.getFetchTimeout())
                .httpLowSpeedLimit(gitCfg.getHttpLowSpeedLimit())
                .httpLowSpeedTime(gitCfg.getHttpLowSpeedTime())
                .allowedSchemes(gitCfg.getAllowedSchemes())
                .sshTimeout(gitCfg.getSshTimeout())
                .sshTimeoutRetryCount(gitCfg.getSshTimeoutRetryCount())
                .build();

        GitTokenProvider authProvider = new AgentGitTokenProvider(gitCfg, apiClientFactory, objectMapper);

        this.providers = new RepositoryProviders(List.of(
                new MavenRepositoryProvider(dependencyManager),
                new GitCliRepositoryProvider(clientCfg, authProvider)
        ));
        this.repositoryCache = new RepositoryCache(cacheCfg.getCacheDir(),
                cacheCfg.getInfoDir(),
                cacheCfg.getLockTimeout(),
                cacheCfg.getMaxAge(),
                cacheCfg.getLockCount(),
                objectMapper);
    }

    public void export(String repoUrl, String branch, String commitId, String repoPath, Path dest, SecretDefinition secretDefinition, List<String> ignorePatterns) throws ExecutionException {
        if (gitCfg.isSkip()) {
            log.info("Skipping git export, using local state");
            return;
        }

        Secret secret = getSecret(secretDefinition);

        Path cacheDir = repositoryCache.getPath(repoUrl);

        repositoryCache.withLock(repoUrl, () -> {
            Repository repo = providers.fetch(
                    FetchRequest.builder()
                            .url(repoUrl)
                            .version(FetchRequest.Version.commitWithBranch(commitId, branch))
                            .secret(secret)
                            .destination(cacheDir)
                            .shallow(gitCfg.isShallowClone())
                            .checkAlreadyFetched(gitCfg.isCheckAlreadyFetched())
                            .build(),
                    repoPath);
            repo.export(dest, ignorePatterns);
            return null;
        });
        repositoryCache.cleanup();
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
