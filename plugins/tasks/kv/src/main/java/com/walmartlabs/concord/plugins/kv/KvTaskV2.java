package com.walmartlabs.concord.plugins.kv;

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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ProcessKvStoreApi;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named("kv")
@SuppressWarnings("unused")
public class KvTaskV2 implements Task {
    private final ApiClient apiClient;
    private final UUID processInstanceId;

    @Inject
    public KvTaskV2(ApiClient apiClient, Context context) {
        this.apiClient = apiClient;
        this.processInstanceId = context.processInstanceId();
    }

    public void remove(String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClient);
        KvTaskUtils.remove(api, processInstanceId, key);
    }

    public void putString(String key, String value) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClient);
        KvTaskUtils.putString(api, processInstanceId, key, value);
    }

    public String getString(String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClient);
        return KvTaskUtils.getString(api, processInstanceId, key);
    }

    public void putLong(String key, Long value) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClient);
        KvTaskUtils.putLong(api, processInstanceId, key, value);
    }

    public Long getLong(String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClient);
        return KvTaskUtils.getLong(api, processInstanceId, key);
    }

    public long inc(String key) throws Exception {
        return incLong(key);
    }

    public long incLong(String key) throws Exception {
        ProcessKvStoreApi api = new ProcessKvStoreApi(apiClient);
        return KvTaskUtils.incLong(api, processInstanceId, key);
    }
}
