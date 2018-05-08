package com.walmartlabs.concord.server.client;

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
import com.walmartlabs.concord.server.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public final class ClientUtils {

    private static final Logger log = LoggerFactory.getLogger(ClientUtils.class);

    public static <T> T withRetry(int retryCount, long retryInterval, Callable<T> c) throws ApiException {
        Exception exception = null;
        int tryCount = 0;
        while (!Thread.currentThread().isInterrupted() && tryCount < retryCount + 1) {
            try {
                return c.call();
            } catch (Exception e) {
                log.error("call error, retry after {} sec", retryInterval / 1000, e);
                exception = e;
                sleep(retryInterval);
            }
            tryCount++;
        }

        throw new ApiException(exception);
    }

    public static String getHeader(String name, ApiResponse<?> resp) {
        return resp.getHeaders().get(name).get(0);
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ClientUtils() {
    }
}
