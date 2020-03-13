package com.walmartlabs.concord.runtime.v2.runner.remote;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ApiClientConfiguration;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.runtime.v2.runner.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Named
@Singleton
public class ApiClientProvider implements Provider<ApiClient> {

    private final ApiClientFactory clientFactory;
    private final WorkingDirectory workDir;

    @Inject
    public ApiClientProvider(ApiClientFactory clientFactory, WorkingDirectory workDir) {
        this.clientFactory = clientFactory;
        this.workDir = workDir;
    }

    @Override
    public ApiClient get() {
        return clientFactory.create(ApiClientConfiguration.builder()
                .sessionToken(getSessionToken(workDir.getValue()))
                .build());
    }

    private static String getSessionToken(Path baseDir) {
        Path p = baseDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(Constants.Files.SESSION_TOKEN_FILE_NAME);

        try {
            return new String(Files.readAllBytes(p));
        } catch (IOException e) {
            throw new RuntimeException("Error while reading the session token file: " + p, e);
        }
    }
}
