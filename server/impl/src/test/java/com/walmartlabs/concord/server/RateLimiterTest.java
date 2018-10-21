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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RateLimiterTest {

    @Test(timeout = 60000)
    public void test() throws Exception {
        RateLimiter l = new RateLimiter(1);

        assertTrue(l.tryAcquire(1000));

        long t1 = System.currentTimeMillis();
        assertTrue(l.tryAcquire(1000));
        long t2 = System.currentTimeMillis();
        assertTrue(t2 - t1 >= 1000);

        Thread.sleep(1000);

        assertTrue(l.tryAcquire(1));
        assertFalse(l.tryAcquire(1));
    }
}
