package com.walmartlabs.concord.server.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.walmartlabs.concord.server.PeriodicTask;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class RepositoryCacheCleanupTask extends PeriodicTask {

    private static final long ERROR_DELAY = TimeUnit.SECONDS.toMillis(30);

    private final RepositoryManager repositoryManager;

    @Inject
    public RepositoryCacheCleanupTask(RepositoryManager repositoryManager) {
        super(repositoryManager.cleanupInterval(), ERROR_DELAY);
        this.repositoryManager = repositoryManager;
    }

    @Override
    protected boolean performTask() throws Exception {
        repositoryManager.cleanup();
        return false;
    }
}
