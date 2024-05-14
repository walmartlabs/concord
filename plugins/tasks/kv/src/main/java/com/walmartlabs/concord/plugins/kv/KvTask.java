package com.walmartlabs.concord.plugins.kv;

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

import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.client2.ProcessKvStoreApi;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named("kv")
public class KvTask implements Task {

    private final ApiClientFactory apiClientFactory;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    private Context context;

    @Inject
    public KvTask(ApiClientFactory apiClientFactory) {
        this.apiClientFactory = apiClientFactory;
    }

    /**
     * @deprecated left for backward compatibility, prefer {@link #remove(Context, String)}
     */
    @Deprecated
    public void remove(@InjectVariable("txId") String instanceId, String key) throws Exception {
        remove(context, key);
    }

    public void remove(@InjectVariable("context") Context ctx, String key) throws Exception {
        ProcessKvStoreApi api = getApi(ctx);
        UUID txId = ContextUtils.getTxId(ctx);
        KvTaskUtils.remove(api, txId, key);
    }

    /**
     * @deprecated left for backward compatibility, prefer {@link #putString(Context, String, String)}
     */
    @Deprecated
    public void putString(@InjectVariable("txId") String instanceId, String key, String value) throws Exception {
        putString(context, key, value);
    }

    public void putString(@InjectVariable("context") Context ctx, String key, String value) throws Exception {
        ProcessKvStoreApi api = getApi(ctx);
        UUID txId = ContextUtils.getTxId(ctx);
        KvTaskUtils.putString(api, txId, key, value);

    }

    /**
     * @deprecated left for backward compatibility, prefer {@link #getString(Context, String)}
     */
    @Deprecated
    public String getString(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return getString(context, key);
    }

    public String getString(@InjectVariable("context") Context ctx, String key) throws Exception {
        ProcessKvStoreApi api = getApi(ctx);
        UUID txId = ContextUtils.getTxId(ctx);
        return KvTaskUtils.getString(api, txId, key);
    }

    /**
     * @deprecated left for backward compatibility, prefer {@link #putLong(Context, String, Long)}
     */
    @Deprecated
    public void putLong(@InjectVariable("txId") String instanceId, String key, Long value) throws Exception {
        putLong(context, key, value);
    }

    public void putLong(@InjectVariable("context") Context ctx, String key, Long value) throws Exception {
        ProcessKvStoreApi api = getApi(ctx);
        UUID txId = ContextUtils.getTxId(ctx);
        KvTaskUtils.putLong(api, txId, key, value);
    }

    /**
     * @deprecated left for backward compatibility, prefer {@link #getLong(Context, String)}
     */
    @Deprecated
    public Long getLong(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return getLong(context, key);
    }

    public Long getLong(@InjectVariable("context") Context ctx, String key) throws Exception {
        ProcessKvStoreApi api = getApi(ctx);
        UUID txId = ContextUtils.getTxId(ctx);
        return KvTaskUtils.getLong(api, txId, key);
    }

    /**
     * @deprecated left for backward compatibility, prefer {@link #inc(Context, String)}
     */
    @Deprecated
    public long inc(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return inc(context, key);
    }

    public long inc(@InjectVariable("context") Context ctx, String key) throws Exception {
        return incLong(ctx, key);
    }

    /**
     * @deprecated left for backward compatibility, prefer {@link #incLong(Context, String)}
     */
    @Deprecated
    public long incLong(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return incLong(context, key);
    }

    public long incLong(@InjectVariable("context") Context ctx, String key) throws Exception {
        ProcessKvStoreApi api = getApi(ctx);
        UUID txId = ContextUtils.getTxId(ctx);
        return KvTaskUtils.incLong(api, txId, key);
    }

    private ProcessKvStoreApi getApi(Context ctx) {
        return new ProcessKvStoreApi(apiClientFactory.create(ApiClientConfiguration.builder()
                .sessionToken(ContextUtils.getSessionToken(ctx))
                .build()));
    }
}
