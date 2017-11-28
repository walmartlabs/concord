package com.walmartlabs.concord.server.project.kv;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.org.project.KvDao;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

@Ignore("requires a local DB instance")
public class KvDaoTest extends AbstractDaoTest {

    @Test(timeout = 10000)
    public void test() throws Exception {
        KvDao kvDao = new KvDao(getConfiguration());

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
    }
}
