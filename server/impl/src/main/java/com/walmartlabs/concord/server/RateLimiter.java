package com.walmartlabs.concord.server;

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

/**
 * A (very) dumb rate limiter.
 */
public class RateLimiter {

    private static final long RATE_DELAY = 1000;

    private final long[] permits;

    private int currentIndex = 0;

    public RateLimiter(int ratePerSecond) {
        if (ratePerSecond <= 0) {
            throw new IllegalStateException("Rate must a positive number, got: " + ratePerSecond);
        }

        this.permits = new long[ratePerSecond];
    }

    public boolean tryAcquire(long timeout) {
        long start = System.currentTimeMillis();
        long end = start + timeout;

        while (true) {
            long t = System.currentTimeMillis();

            synchronized (permits) {
                long p = permits[currentIndex];

                if (t > p) {
                    permits[currentIndex] = System.currentTimeMillis() + RATE_DELAY;

                    currentIndex += 1;
                    if (currentIndex >= permits.length) {
                        currentIndex = 0;
                    }

                    return true;
                }
            }

            if (t > end) {
                return false;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
