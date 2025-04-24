package com.walmartlabs.concord.server.process.queue.dispatcher;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DispatcherTest {

    private static List<Dispatcher.Request> requests;

    @Mock
    private Dispatcher.RequirementsMatcherErrorHandler errHandler;

    @BeforeAll
    static void setup() {
        var request = new ProcessRequest(Map.of("flavor", "default"));
        requests = List.of(new Dispatcher.Request(null, request));
    }

    @Test
    void testInvalidInvalidRegex() {
        var candidate = generateCandidate("*invalidRegex*");
        assertNull(Dispatcher.findRequest(candidate, requests, errHandler));
        verify(errHandler, times(1)).handle(any(), any());
    }

    @Test
    void testValid() {
        var candidate = generateCandidate(".*default.*");
        assertNotNull(Dispatcher.findRequest(candidate, requests, errHandler));
        verify(errHandler, times(0)).handle(any(), any());
    }

    private static ProcessQueueEntry generateCandidate(String flavor) {
        return ProcessQueueEntry.builder()
                .requirements(Map.of(
                        "agent", Map.of(
                                "flavor", List.of(flavor)
                        )
                ))
                .key(ProcessKey.random())
                .build();
    }

}
