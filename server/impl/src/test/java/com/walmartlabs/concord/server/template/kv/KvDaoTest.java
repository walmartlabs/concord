package com.walmartlabs.concord.server.template.kv;

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

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.Locks;
import com.walmartlabs.concord.server.cfg.LockingConfiguration;
import com.walmartlabs.concord.server.org.project.KvDao;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

@Disabled("requires a local DB instance")
public class KvDaoTest extends AbstractDaoTest {

    @Test
    public void test() throws Exception {
        assertTimeout(Duration.ofMillis(10000), () -> {
            KvDao kvDao = new KvDao(getConfiguration(), new Locks(new LockingConfiguration(8)));

            UUID projectId = UUID.randomUUID();
            String key = "key_" + System.currentTimeMillis();

            int threads = 3;
            int iterations = 50;

            AtomicLong counter = new AtomicLong(0);

            Runnable r = () -> {
                for (int i = 0; i < iterations; i++) {
                    kvDao.inc(projectId, key);
                    counter.incrementAndGet();
                }
            };

            Thread[] workes = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                workes[i] = new Thread(r);
                workes[i].start();
            }

            for (Thread w : workes) {
                w.join();
            }

            Long total = counter.get();
            assertEquals(total, kvDao.getLong(projectId, key));
        });
    }
}
