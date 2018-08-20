package com.walmartlabs.concord.server.process.queue;

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
import com.walmartlabs.concord.server.org.policy.PolicyDao;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessKind;
import com.walmartlabs.concord.server.process.ProcessStatus;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

@Ignore("requires a local DB instance")
public class ProcessQueueDaoTest extends AbstractDaoTest {

    private ProcessQueueDao queueDao;
    private ProjectDao projectDao;

    @Before
    public void setUp() throws Exception {
        queueDao = new ProcessQueueDao(getConfiguration(), mock(PolicyDao.class));
        projectDao = new ProjectDao(getConfiguration());
    }

    @Test
    public void test() throws Exception {
        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;

        String projectName = "project_" + System.currentTimeMillis();
        UUID projectId = projectDao.insert(orgId, projectName, null, null, null, null, true, new byte[0]);

        UUID instanceA = UUID.randomUUID();
        queueDao.insertInitial(instanceA, ProcessKind.DEFAULT, null, projectId, null);
        queueDao.update(instanceA, ProcessStatus.ENQUEUED);

        // add a small delay between two jobs
        Thread.sleep(100);

        UUID instanceB = UUID.randomUUID();
        queueDao.insertInitial(instanceB, ProcessKind.DEFAULT, null, projectId, null);
        queueDao.update(instanceB, ProcessStatus.ENQUEUED);

        // ---

        ProcessEntry e1 = queueDao.poll(null);
        ProcessEntry e2 = queueDao.poll(null);
        ProcessEntry e3 = queueDao.poll(null);

        assertNotNull(e1);
        assertEquals(instanceA, e1.getInstanceId());

        assertNotNull(e2);
        assertEquals(instanceB, e2.getInstanceId());

        assertNull(e3);
    }
}
