package com.walmartlabs.concord.server.history;

import com.walmartlabs.concord.server.api.history.ProcessHistoryEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.AbstractDaoTest;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class ProcessHistoryDaoTest extends AbstractDaoTest {

    private ProcessHistoryDao dao;

    @Before
    public void setUp() throws Exception {
        dao = new ProcessHistoryDao(getConfiguration());
    }

    @Test
    public void testInsert() throws Exception {
        List<ProcessHistoryEntry> l = dao.list(-1, ProcessStatus.RUNNING);
        assertNotNull(l);
        assertTrue(l.isEmpty());

        // ---

        String i1 = UUID.randomUUID().toString();
        String i2 = UUID.randomUUID().toString();

        // ---

        dao.insertInitial(i1, "project", "a", "n/a");
        dao.update(i1, ProcessStatus.RUNNING);

        ProcessHistoryEntry e1 = dao.get(i1);
        assertNotNull(e1);
        assertEquals(ProcessStatus.RUNNING, e1.getStatus());

        // ---

        dao.insertInitial(i2, "project", "b", "n/a");
        dao.update(i2, ProcessStatus.FAILED);

        ProcessHistoryEntry e2 = dao.get(i2);
        assertNotNull(e2);
        assertEquals(ProcessStatus.FAILED, e2.getStatus());

        // ---

        List<ProcessHistoryEntry> l1 = dao.list(-1);
        assertNotNull(l1);
        assertEquals(2, l1.size());

        List<ProcessHistoryEntry> l2 = dao.list(-1, ProcessStatus.RUNNING);
        assertNotNull(l2);
        assertEquals(1, l2.size());
    }

    @Test
    public void testUpdate() throws Exception {
        List<ProcessHistoryEntry> l = dao.list(-1, ProcessStatus.RUNNING);
        assertNotNull(l);
        assertTrue(l.isEmpty());

        // ---

        String id = UUID.randomUUID().toString();

        // ---

        dao.insertInitial(id, "project", "a", "n/a");
        dao.update(id, ProcessStatus.RUNNING);

        ProcessHistoryEntry e1 = dao.get(id);
        assertNotNull(e1);
        assertEquals(ProcessStatus.RUNNING, e1.getStatus());

        // ---

        dao.update(id, ProcessStatus.FAILED);

        // ---

        List<ProcessHistoryEntry> l1 = dao.list(-1, ProcessStatus.FAILED);
        assertNotNull(l1);
        assertEquals(1, l1.size());
    }
}
