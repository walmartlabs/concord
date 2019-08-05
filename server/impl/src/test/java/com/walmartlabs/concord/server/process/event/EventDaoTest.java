package com.walmartlabs.concord.server.process.event;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.TestObjectMapper;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueLock;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;

@Ignore
public class EventDaoTest extends AbstractDaoTest {

    private ProcessQueueDao processQueueDao;
    private EventDao eventDao;

    @Before
    public void setUp() {
        processQueueDao = new ProcessQueueDao(getConfiguration(), Collections.emptyList(), mock(EventDao.class), mock(ProcessQueueLock.class), new ConcordObjectMapper(TestObjectMapper.INSTANCE));
        eventDao = new EventDao(getConfiguration(), new ConcordObjectMapper(TestObjectMapper.INSTANCE));
    }

    @Test
    public void testInsert() {
        ProcessKey pk = processQueueDao.getKey(UUID.fromString("57cd23b4-3c04-4254-87a2-432a5a8c22f5"));
        eventDao.insert(pk, "TEST", null, Collections.singletonMap("k", "v"));
    }

    @Test
    public void testBatchInsert() {
        ProcessKey pk = processQueueDao.getKey(UUID.fromString("57cd23b4-3c04-4254-87a2-432a5a8c22f5"));
        List<ProcessEventRequest> events = new ArrayList<>();
        events.add(new ProcessEventRequest("TEST", null, Collections.singletonMap("k1", "v1")));
        events.add(new ProcessEventRequest("TEST", null, Collections.singletonMap("k2", "v2")));

        eventDao.insert(pk, events);
    }
}
