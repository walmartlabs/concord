package com.walmartlabs.concord.server.process.logs;

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
