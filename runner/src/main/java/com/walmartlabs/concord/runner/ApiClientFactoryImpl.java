package com.walmartlabs.concord.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.ApiClientFactory;
import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.server.ApiClient;
import com.walmartlabs.concord.server.client.ConcordApiClient;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;

@Named
public class ApiClientFactoryImpl implements ApiClientFactory {

    @Inject
    ApiConfiguration cfg;

    @Override
    public ApiClient create(Context ctx) {
        Path tmpDir;
        try {
            tmpDir = IOUtils.createTempDir("task-client");
        } catch (IOException e) {
            throw new RuntimeException("Can't create tmp dir", e);
        }

        ConcordApiClient client = new ConcordApiClient();
        client.setTempFolderPath(tmpDir.toString());
        client.setBasePath(cfg.getBaseUrl());
        client.setSessionToken(cfg.getSessionToken(ctx));
        return client;
    }
}
