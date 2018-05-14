package com.walmartlabs.concord.plugins.kv;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.*;
import com.walmartlabs.concord.server.ApiClient;
import com.walmartlabs.concord.server.client.ClientUtils;
import com.walmartlabs.concord.server.client.ConcordApiClient;
import com.walmartlabs.concord.server.client.ProcessKvStoreApi;
import com.walmartlabs.concord.server.client.ProcessKvStoreApi;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;

@Named("kv")
public class KvTask implements Task {

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    @Inject
    ApiConfiguration cfg;

    public void remove(@InjectVariable("txId") String instanceId, String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(createClient(cfg, context));

        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            api.removeKey(instanceId, key);
            return null;
        });
    }

    public void putString(@InjectVariable("txId") String instanceId, String key, String value) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(createClient(cfg, context));

        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            api.putString(instanceId, key, value);
            return null;
        });
    }

    public String getString(@InjectVariable("txId") String instanceId, String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(createClient(cfg, context));

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> api.getString(instanceId, key));
    }

    public void putLong(@InjectVariable("txId") String instanceId, String key, Long value) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(createClient(cfg, context));

        ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            api.putLong(instanceId, key, value);
            return null;
        });
    }

    public long inc(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return incLong(instanceId, key);
    }

    public long incLong(@InjectVariable("txId") String instanceId, String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(createClient(cfg, context));

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> api.incLong(instanceId, key));
    }

    public Long getLong(@InjectVariable("txId") String instanceId, String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(createClient(cfg, context));

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> api.getLong(instanceId, key));
    }

    private ApiClient createClient(ApiConfiguration cfg, Context ctx) throws IOException {
        ConcordApiClient client = new ConcordApiClient();
        client.setTempFolderPath(IOUtils.createTempDir("kv-task-client").toString());
        client.setBasePath(cfg.getBaseUrl());
        client.setSessionToken(cfg.getSessionToken(ctx));
        return client;
    }
}
