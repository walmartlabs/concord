package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.runtime.v2.model.EventConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class EventReportingServiceTest {

    @Test
    void testSingle() {
        var evs = getService(1);
        evs.report(new ProcessEventRequest());
        evs.report(new ProcessEventRequest());
        evs.report(new ProcessEventRequest());
        evs.report(new ProcessEventRequest());

        assertEquals(4, evs.flushCounter.get());

        evs.afterProcessEnds(null, null, null);

        assertEquals(5, evs.flushCounter.get());
    }

    @Test
    void testBatch() {
        var evs = getService(2);
        evs.report(new ProcessEventRequest());
        evs.report(new ProcessEventRequest());
        evs.report(new ProcessEventRequest());
        evs.report(new ProcessEventRequest());

        assertEquals(2, evs.flushCounter.get());

        evs.afterProcessEnds(null, null, null);

        assertEquals(3, evs.flushCounter.get());
    }

    private static MockedEventReportingService getService(int batchSize) {
        ProcessConfiguration processCfg = ProcessConfiguration.builder()
                .events(EventConfiguration.builder()
                        .batchSize(batchSize)
                        .build())
                .build();

        return new MockedEventReportingService(processCfg);
    }

    private static class MockedEventReportingService extends DefaultEventReportingService {
        private final AtomicInteger flushCounter;

        public MockedEventReportingService(ProcessConfiguration processConfiguration) {
            super(processConfiguration, mock(ProcessEventWriter.class));
            this.flushCounter = new AtomicInteger();
        }

        @Override
        void flush() {
            super.flush();
            flushCounter.incrementAndGet();
        }
    }
}
