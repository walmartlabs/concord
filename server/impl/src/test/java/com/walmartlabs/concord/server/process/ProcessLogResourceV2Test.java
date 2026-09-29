package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLog;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessLogResourceV2Test {

    @Test
    void returnsTheActualCursorForAnEmptySuffixOfAnEmptyLog() {
        ProcessLog log = new ProcessLog(0, List.of());

        try (Response response = ProcessLogResourceV2.toResponse(UUID.randomUUID(), 0, log)) {
            assertEquals("bytes 0-0/0", response.getHeaderString("Content-Range"));
        }
    }

    @Test
    void returnsTheActualCursorForAnOffsetBeyondTheLog() {
        ProcessLog log = new ProcessLog(12, List.of());

        try (Response response = ProcessLogResourceV2.toResponse(UUID.randomUUID(), 0, log)) {
            assertEquals("bytes 12-12/12", response.getHeaderString("Content-Range"));
        }
    }
}
