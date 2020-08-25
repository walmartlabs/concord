package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import org.junit.Test;

import java.nio.file.Paths;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class LogSegmentNameParserTest {

    @Test
    public void testSuccess() {
        LogSegmentNameParser parser = new LogSegmentNameParser();

        UUID correlationId = UUID.randomUUID();
        String taskName = "test-task_Oo";
        Date createdAt = new Date();
        String fileName = correlationId + "-" + taskName + "_" + createdAt.getTime() + ".log";

        LogSegment segment = parser.parse(Paths.get(fileName));
        assertEquals(correlationId, segment.correlationId());
        assertEquals(taskName, segment.name());
        assertEquals(createdAt, segment.createdAt());
    }
}
