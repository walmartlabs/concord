package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.RepositoryExporter;
import com.walmartlabs.concord.repository.*;
import com.walmartlabs.concord.repository.auth.HttpAuthProvider;
import com.walmartlabs.concord.sdk.Secret;

import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class CliRepositoryExporter implements RepositoryExporter {

    // TODO: move to configuration
    private static final Duration DEFAULT_OPERATION_TIMEOUT = Duration.parse("PT10M");
    private static final Duration FETCH_TIMEOUT = Duration.parse("PT10M");
    private static final int HTTP_LOW_SPEED_LIMIT = 0;
    private static final Duration HTTP_LOW_SPEED_TIME = Duration.ofMinutes(10);
    private static final Duration SSH_TIMEOUT = Duration.ofMinutes(10);
    private static final int SSH_TIMEOUT_RETRY_COUNT = 1;

    private final Path repoCacheDir;

    private final RepositoryProviders providers;

    public CliRepositoryExporter(Path repoCacheDir) {
        this.repoCacheDir = repoCacheDir;

        GitClientConfiguration clientCfg = GitClientConfiguration.builder()
                .oauthToken(null)
                .defaultOperationTimeout(DEFAULT_OPERATION_TIMEOUT)
                .fetchTimeout(FETCH_TIMEOUT)
                .httpLowSpeedLimit(HTTP_LOW_SPEED_LIMIT)
                .httpLowSpeedTime(HTTP_LOW_SPEED_TIME)
                .sshTimeout(SSH_TIMEOUT)
                .sshTimeoutRetryCount(SSH_TIMEOUT_RETRY_COUNT)
                .build();

        HttpAuthProvider authProvider = new HttpAuthProvider() {
            @Override
            public boolean canHandle(String gitHost) {
                return false;
            }

            @Override
            public String get(String gitHost, @Nullable Secret secret) {
                throw new UnsupportedOperationException("Not supported");
            }
        };

        this.providers = new RepositoryProviders(List.of(new GitCliRepositoryProvider(clientCfg, authProvider)));
    }

    @Override
    public Snapshot export(Import.GitDefinition entry, Path workDir) throws Exception {
        Path dest = workDir;
        if (entry.dest() != null) {
            dest = dest.resolve(Objects.requireNonNull(entry.dest()));
        }

        String url = Objects.requireNonNull(entry.url());
        Path cacheDir = repoCacheDir.resolve(encodeUrl(url));
        Secret secret = null;

        Repository repo = providers.fetch(
                FetchRequest.builder()
                        .url(url)
                        .version(FetchRequest.Version.from(entry.version()))
                        .secret(secret)
                        .destination(cacheDir)
                .build(),
                entry.path());
        return repo.export(dest, entry.exclude());
    }

    private static String encodeUrl(String url) {
        String encodedUrl;
        try {
            encodedUrl = URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RepositoryException("Url encoding error", e);
        }

        return encodedUrl;
    }
}
