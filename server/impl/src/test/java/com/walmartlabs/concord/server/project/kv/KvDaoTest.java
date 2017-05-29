package com.walmartlabs.concord.server.project.kv;

import com.walmartlabs.concord.server.AbstractDaoTest;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

public class KvDaoTest extends AbstractDaoTest {

    @Test(timeout = 10000)
    public void test() throws Exception {
        KvDao kvDao = new KvDao(getConfiguration());

        String projectName = "project_" + System.currentTimeMillis();
        String key = "key_" + System.currentTimeMillis();

        int threads = 3;
        int iterations = 50;

        AtomicLong counter = new AtomicLong(0);

        Runnable r = () -> {
            for (int i = 0; i < iterations; i++) {
                kvDao.inc(projectName, key);
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
        assertEquals(total, kvDao.getLong(projectName, key));
    }
}
