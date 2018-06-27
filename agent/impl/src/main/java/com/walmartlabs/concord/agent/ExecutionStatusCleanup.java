package com.walmartlabs.concord.agent;

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


import java.util.concurrent.TimeUnit;

public class ExecutionStatusCleanup implements Runnable {

    private static final long CLEANUP_INTERVAL = TimeUnit.SECONDS.toMillis(30);

    private final ExecutionManager executionManager;

    public ExecutionStatusCleanup(ExecutionManager executionManager) {
        this.executionManager = executionManager;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            executionManager.cleanup();

            try {
                Thread.sleep(CLEANUP_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
