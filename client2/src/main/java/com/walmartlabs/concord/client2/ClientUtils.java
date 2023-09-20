package com.walmartlabs.concord.client2;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public final class ClientUtils {

    private static final Logger log = LoggerFactory.getLogger(ClientUtils.class);

    public static <T> T withRetry(int retryCount, long retryInterval, Callable<T> c) throws ApiException {
        Exception exception = null;
        int tryCount = 0;
        while (!Thread.currentThread().isInterrupted() && tryCount < retryCount + 1) {
            try {
                return c.call();
            } catch (ApiException e) {
                exception = e;

                if (e.getCode() >= 400 && e.getCode() < 500) {
                    break;
                }
                log.warn("call error: '{}'", getErrorMessage(e));
            } catch (Exception e) {
                exception = e;
                log.error("call error", e);
            }
            log.info("retry after {} sec", retryInterval / 1000);
            sleep(retryInterval);
            tryCount++;
        }

        if (exception instanceof ApiException) {
            throw (ApiException) exception;
        }

        throw new ApiException(exception);
    }

    /**
     * Returns a value of the specified header.
     * Only the first value is returned.
     * The header's {@code name} is case-insensitive.
     */
    public static String getHeader(String name, ApiResponse<?> resp) {
        Map<String, List<String>> headers = resp.getHeaders();
        if (headers == null) {
            return null;
        }

        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (!e.getKey().equalsIgnoreCase(name)) {
                continue;
            }

            List<String> values = e.getValue();

            if (values == null || values.isEmpty()) {
                return null;
            }

            return values.get(0);
        }

        return null;
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String getErrorMessage(ApiException e) {
        String error = e.getMessage();
        if (e.getResponseBody() != null && !e.getResponseBody().isEmpty()) {
            error += ": " + e.getResponseBody();
        }
        return error;
    }

    private ClientUtils() {
    }
}
