package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.BackgroundTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PeriodicTask implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(PeriodicTask.class);

    private final long interval;
    private final long errorDelay;

    private Thread worker;

    public PeriodicTask(long interval, long errorDelay) {
        this.interval = interval;
        this.errorDelay = errorDelay;
    }

    @Override
    public void start() {
        if (interval <= 0) {
            log.warn("start -> task is disabled: {}", taskName());
            return;
        }

        this.worker = new Thread(this::run, taskName());
        this.worker.start();
        log.info("start -> done: {}", this.getClass());
    }

    @Override
    public void stop() {
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }

        log.info("stop -> done: {}", taskName());
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                boolean isContinue = performTask();
                if (!isContinue) {
                    sleep(interval);
                }
            } catch (Exception e) {
                log.warn("run -> task {} error: {}. Will retry in {}ms...", taskName(), e.getMessage(), errorDelay, e);
                sleep(errorDelay);
            }
        }
    }

    private String taskName() {
        return this.getClass().getSimpleName();
    }

    protected abstract boolean performTask() throws Exception;

    protected static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

