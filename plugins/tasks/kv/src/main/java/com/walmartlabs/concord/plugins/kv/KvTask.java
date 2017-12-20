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

import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.KvService;
import com.walmartlabs.concord.sdk.RpcClient;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;

@Named("kv")
public class KvTask implements Task {

    private final KvService kvService;

    @Inject
    public KvTask(RpcClient rpcClient) {
        this.kvService = rpcClient.getKvService();
    }

    public void remove(@InjectVariable("txId") String instanceId, String key) throws Exception {
        kvService.remove(instanceId, key);
    }

    public void putString(@InjectVariable("txId") String instanceId, String key, String value) throws Exception {
        kvService.putString(instanceId, key, value);
    }

    public String getString(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return kvService.getString(instanceId, key);
    }

    public void putLong(@InjectVariable("txId") String instanceId, String key, Long value) throws Exception {
        kvService.putLong(instanceId, key, value);
    }

    public long inc(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return incLong(instanceId, key);
    }

    public long incLong(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return kvService.incLong(instanceId, key);
    }

    public Long getLong(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return kvService.getLong(instanceId, key);
    }
}
