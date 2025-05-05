package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.client2.LockResult;
import com.walmartlabs.concord.client2.ProcessLocksApi;
import com.walmartlabs.concord.common.TimeProvider;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.sdk.LockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.Callable;

public class DefaultLockService implements LockService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLockService.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;
    private static final long LOCK_RETRY_INTERVAL = 10000;

    private final InstanceId instanceId;
    private final ApiClient apiClient;
    private final TimeProvider timeProvider;

    @Inject
    public DefaultLockService(InstanceId instanceId, ApiClient apiClient, TimeProvider timeProvider) {
        this.instanceId = instanceId;
        this.apiClient = apiClient;
        this.timeProvider = timeProvider;
    }

    @Override
    public void projectLock(String lockName) throws Exception {
        ProcessLocksApi api = new ProcessLocksApi(apiClient);

        // TODO: timeout
        while (!Thread.currentThread().isInterrupted()) {
            LockResult lock = withRetry(() -> api.tryLock(instanceId.getValue(), lockName, LockScope.PROJECT.name()));
            if (lock.getAcquired()) {
                log.info("successfully acquired lock '{}' in '{}' scope...", lockName, LockScope.PROJECT);
                return;
            }

            log.info("waiting for lock '{}' in '{}' scope...", lockName, LockScope.PROJECT);
            sleep(LOCK_RETRY_INTERVAL);
        }
    }

    @Override
    public void projectUnlock(String lockName) throws Exception {
        ProcessLocksApi api = new ProcessLocksApi(apiClient);
        withRetry(() -> {
            api.unlock(instanceId.getValue(), lockName, LockScope.PROJECT.name());
            return null;
        });
        log.info("unlocking '{}' with scope '{}' -> done", lockName, LockScope.PROJECT);
    }

    private void sleep(long t) {
        try {
            timeProvider.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private enum LockScope {
        ORG, PROJECT
    }

    private static <T> T withRetry(Callable<T> c) throws ApiException {
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, c);
    }
}
