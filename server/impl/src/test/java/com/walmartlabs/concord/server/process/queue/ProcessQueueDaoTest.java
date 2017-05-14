package com.walmartlabs.concord.server.process.queue;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class ProcessQueueDaoTest extends AbstractDaoTest {

    private ProcessQueueDao queueDao;

    @Before
    public void setUp() throws Exception {
        queueDao = new ProcessQueueDao(getConfiguration());
    }

    @Test
    public void test() throws Exception {
        String instanceA = UUID.randomUUID().toString();
        queueDao.insertInitial(instanceA, "testProject", "testInitiator");

        // add a small delay between two jobs
        Thread.sleep(100);

        String instanceB = UUID.randomUUID().toString();
        queueDao.insertInitial(instanceB, "testProject", "testInitiator");

        // ---

        ProcessEntry e1 = queueDao.poll();
        ProcessEntry e2 = queueDao.poll();
        ProcessEntry e3 = queueDao.poll();

        assertNotNull(e1);
        assertEquals(instanceA, e1.getInstanceId());

        assertNotNull(e2);
        assertEquals(instanceB, e2.getInstanceId());

        assertNull(e3);
    }
}
