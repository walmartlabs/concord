package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.client2.ProcessEventsApi;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class EventReportingServiceTest {

    private static final UUID instanceId1 = UUID.randomUUID();
    private static final String sessionToken1 = UUID.randomUUID().toString();

    @Test
    void testSingle() {
        var evs = getService(1);
        evs.report(new ProcessEventRequest(), instanceId1, sessionToken1);
        evs.report(new ProcessEventRequest(), instanceId1, sessionToken1);
        evs.report(new ProcessEventRequest(), instanceId1, sessionToken1);
        evs.report(new ProcessEventRequest(), instanceId1, sessionToken1);

        assertEquals(0, evs.flushCounter.get());

        evs.onFinalize(null);

        assertEquals(1, evs.flushCounter.get());
        assertEquals(4, evs.sendSingleCounter.get());
    }

    @Test
    void testBatch() {
        var evs = getService(2);
        evs.report(new ProcessEventRequest(), instanceId1, sessionToken1);
        evs.report(new ProcessEventRequest(), instanceId1, sessionToken1);
        evs.report(new ProcessEventRequest(), instanceId1, sessionToken1);
        evs.report(new ProcessEventRequest(), instanceId1, sessionToken1);

        assertEquals(2, evs.flushCounter.get());

        evs.onFinalize(null);

        assertEquals(3, evs.flushCounter.get());
        assertEquals(0, evs.sendSingleCounter.get());
    }

    private static MockedEventReportingService getService(int batchSize) {
        return new MockedEventReportingService(batchSize, 30);
    }

    private static class MockedEventReportingService extends DefaultEventReportingService {
        final ProcessEventsApi mockProcessEventsApi;
        private final AtomicInteger flushCounter;
        private final AtomicInteger sendSingleCounter;

        public MockedEventReportingService(int batchSize, int duration) {
            super(mock(ApiClientFactory.class), batchSize, duration);
            this.mockProcessEventsApi = mock(ProcessEventsApi.class);
            this.flushCounter = new AtomicInteger();
            this.sendSingleCounter = new AtomicInteger();
        }

        @Override
        void flush() {
            super.flush();
            flushCounter.incrementAndGet();
        }

        @Override
        void sendSingle(ProcessEventRequest req, UUID instanceId, String sessionToken) {
            super.sendSingle(req, instanceId, sessionToken);
            sendSingleCounter.incrementAndGet();
        }

        @Override
        ProcessEventsApi getProcessEventsApi(String sessionToken) {
            return mockProcessEventsApi;
        }
    }

}
