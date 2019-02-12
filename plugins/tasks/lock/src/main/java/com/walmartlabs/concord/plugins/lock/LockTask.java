package com.walmartlabs.concord.plugins.lock;

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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.LockResult;
import com.walmartlabs.concord.client.ProcessLocksApi;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;
import java.util.concurrent.Callable;

@Named("lock")
public class LockTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(LockTask.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    private final ApiClientFactory apiClientFactory;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    @Inject
    public LockTask(ApiClientFactory apiClientFactory) {
        this.apiClientFactory = apiClientFactory;
    }

    public void lock(@InjectVariable("txId") String instanceId, String lockName, String scope) throws Exception {
        ProcessLocksApi api = new ProcessLocksApi(apiClientFactory.create(context));

        log.info("Locking '{}' with scope '{}'", lockName, scope);

        LockResult lock = withRetry(() -> api.tryLock(UUID.fromString(instanceId), lockName, scope));

        log.info("Locking '{}' with scope '{}' -> {}", lockName, scope, lock.isAcquired());

        if (lock.isAcquired()) {
            return;
        }

        context.suspend(lockName);
    }

    public void unlock(@InjectVariable("txId") String instanceId, String lockName, String scope) throws Exception {
        ProcessLocksApi api = new ProcessLocksApi(apiClientFactory.create(context));

        log.info("Unlocking '{}' with scope '{}'", lockName, scope);
        withRetry(() -> {
            api.unlock(UUID.fromString(instanceId), lockName, scope);
            return null;
        });
        log.info("Unlocking '{}' with scope '{}' -> done", lockName, scope);
    }

    private static <T> T withRetry(Callable<T> c) throws ApiException {
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, c);
    }
}
