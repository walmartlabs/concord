package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton container environment for sharing a single Concord container set
 * across multiple test classes. This significantly reduces test execution time
 * by avoiding container startup overhead for each test class.
 * <p>
 * The container is started lazily on first acquisition and kept running until
 * the JVM exits (Testcontainers' Ryuk handles cleanup).
 *
 * @see SharedConcordExtension
 */
public final class SharedConcordEnvironment {

    private static final AtomicReference<ConcordRule> INSTANCE = new AtomicReference<>();
    private static final AtomicInteger REFERENCE_COUNT = new AtomicInteger(0);

    /**
     * Acquires access to the shared Concord environment.
     * Starts the container on first call.
     *
     * @return the shared ConcordRule instance
     */
    public static synchronized ConcordRule acquire() {
        if (INSTANCE.get() == null) {
            ConcordRule rule = ConcordConfiguration.configure();
            rule.start();
            INSTANCE.set(rule);
        }
        REFERENCE_COUNT.incrementAndGet();
        return INSTANCE.get();
    }

    /**
     * Releases access to the shared Concord environment.
     * The container is not actually stopped - Ryuk handles cleanup at JVM shutdown.
     */
    public static synchronized void release() {
        REFERENCE_COUNT.decrementAndGet();
    }

    private SharedConcordEnvironment() {
    }
}
