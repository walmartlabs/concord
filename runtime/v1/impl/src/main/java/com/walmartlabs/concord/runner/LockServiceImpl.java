package com.walmartlabs.concord.runner;

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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.LockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;
import java.util.concurrent.Callable;

@Named
public class LockServiceImpl implements LockService {

    private static final Logger log = LoggerFactory.getLogger(LockServiceImpl.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;
    private static final long LOCK_RETRY_INTERVAL = 10000; // TODO custom intervals?

    private final ApiClientFactory apiClientFactory;

    private enum LockScope {
        ORG, PROJECT
    }

    @Inject
    public LockServiceImpl(ApiClientFactory apiClientFactory) {
        this.apiClientFactory = apiClientFactory;
    }

    @Override
    public void projectLock(Context ctx, String lockName) throws Exception {
        ProcessLocksApi api = new ProcessLocksApi(apiClientFactory.create(ApiClientConfiguration.builder()
                .sessionToken(ContextUtils.getSessionToken(ctx))
                .build()));

        // TODO: timeout
        UUID instanceId = ContextUtils.getTxId(ctx);
        while (!Thread.currentThread().isInterrupted()) {
            LockResult lock = withRetry(() -> api.tryLock(instanceId, lockName, LockScope.PROJECT.name()));
            if (lock.getAcquired()) {
                log.info("successfully acquired lock '{}' in '{}' scope...", lockName, LockScope.PROJECT);
                return;
            }

            log.info("waiting for lock '{}' in '{}' scope...", lockName, LockScope.PROJECT);
            sleep(LOCK_RETRY_INTERVAL);
        }
    }

    @Override
    public void projectUnlock(Context ctx, String lockName) throws Exception {
        ProcessLocksApi api = new ProcessLocksApi(apiClientFactory.create(ApiClientConfiguration.builder()
                .sessionToken(ContextUtils.getSessionToken(ctx))
                .build()));

        UUID instanceId = ContextUtils.getTxId(ctx);
        withRetry(() -> {
            api.unlock(instanceId, lockName, LockScope.PROJECT.name());
            return null;
        });
        log.info("unlocking '{}' with scope '{}' -> done", lockName, LockScope.PROJECT);
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static <T> T withRetry(Callable<T> c) throws ApiException {
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, c);
    }
}
