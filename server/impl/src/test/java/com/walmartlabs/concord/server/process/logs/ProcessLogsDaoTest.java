package com.walmartlabs.concord.server.process.logs;

import com.walmartlabs.concord.server.AbstractDaoTest;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Ignore
public class ProcessLogsDaoTest extends AbstractDaoTest {

    @Test
    public void testAppend() throws Exception {
        ProcessLogsDao processLogsDao = new ProcessLogsDao(getConfiguration());

        int files = 100;
        int chunks = 10;
        int chunkSize = 100;

        byte[] data = new byte[chunkSize];
        ThreadLocalRandom.current().nextBytes(data);

        for (int i = 0; i < files; i++) {
            UUID instanceId = UUID.randomUUID();

            for (int j = 0; j < chunks; j++) {
                processLogsDao.append(instanceId, data);
            }

            if (i % 10 == 0) {
                System.out.println(i);
            }
        }
    }
}
