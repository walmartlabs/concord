package com.walmartlabs.concord.server;

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

import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.events.ProcessEvent;
import com.walmartlabs.concord.server.sdk.events.ProcessEventListener;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class ListenersTest {

    @Test
    public void test() {
        List<ProcessEvent> receivedEvents = new ArrayList<>();
        Collection<ProcessEventListener> processEventListeners = Collections.singletonList(events -> {
            synchronized (receivedEvents) {
                receivedEvents.addAll(events);
            }
        });

        Listeners listeners = new Listeners(processEventListeners, Collections.emptyList(), Collections.emptyList());
        listeners.onProcessEvent(Collections.singletonList(ProcessEvent.builder()
                .processKey(new ProcessKey(UUID.randomUUID(), OffsetDateTime.now()))
                .eventSeq(0)
                .eventDate(OffsetDateTime.now())
                .eventType("TEST")
                .data(Collections.singletonMap("x", 123))
                .build()));

        assertEquals(1, receivedEvents.size());
    }
}
