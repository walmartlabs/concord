package com.walmartlabs.concord.server.process.queue;

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
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;

@Disabled("requires a local DB instance")
public class ProcessKeyCacheTest extends AbstractDaoTest {

    @Test
    public void testNotFound() {
        ProcessQueueDao dao = new ProcessQueueDao(getConfiguration(), new ConcordObjectMapper(TestObjectMapper.INSTANCE));
        ProcessKeyCache keyCache = new ProcessKeyCache(dao);

        ProcessKey key = keyCache.get(UUID.randomUUID());
        assertNull(key);
    }
}
