package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class AbstractQueueClient<E> extends AbstractClient {

    private static final Logger log = LoggerFactory.getLogger(AbstractQueueClient.class);

    private static final long ERROR_DELAY = 5000;

    private final long pollInterval;

    public AbstractQueueClient(Configuration cfg) throws IOException {
        super(cfg);

        this.pollInterval = cfg.getPollInterval();
    }

    public E take() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                E entity = poll();

                if (entity == null) {
                    sleep(pollInterval);
                } else {
                    return entity;
                }
            } catch (Exception e) {
                log.error("take -> error, retry after {} sec", ERROR_DELAY / 1000, e);
                sleep(ERROR_DELAY);
            }
        }

        return null;
    }

    protected abstract E poll() throws ApiException;
}
