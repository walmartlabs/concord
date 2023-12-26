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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.server.Listeners;
import com.walmartlabs.concord.server.sdk.events.ProcessEvent;
import com.walmartlabs.concord.server.sdk.metrics.InjectMeter;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

public class ProcessEventManager {

    private final ProcessEventDao eventDao;
    private final Listeners listeners;

    private final Histogram batchInsertHistogram;

    @InjectMeter
    private final Meter eventsReceived;

    @Inject
    public ProcessEventManager(ProcessEventDao eventDao,
                               Listeners listeners,
                               Meter eventsReceived,
                               MetricRegistry metricRegistry) {

        this.eventDao = eventDao;
        this.listeners = listeners;
        this.eventsReceived = eventsReceived;

        this.batchInsertHistogram = metricRegistry.histogram("process-events-batch-insert");
    }

    @WithTimer
    public void event(List<NewProcessEvent> events) {
        List<ProcessEvent> insertedEvents = eventDao.txResult(tx -> doEvent(tx, events));
        listeners.onProcessEvent(insertedEvents);
    }

    public void event(DSLContext tx, NewProcessEvent event) {
        event(tx, Collections.singletonList(event));
    }

    @WithTimer
    public void event(DSLContext tx, List<NewProcessEvent> events) {
        // TODO consider returning a callback that can be called outside of the transaction
        List<ProcessEvent> insertedEvents = doEvent(tx, events);
        listeners.onProcessEvent(insertedEvents);
    }

    public List<ProcessEventEntry> list(ProcessEventFilter filter) {
        return eventDao.list(filter);
    }

    private List<ProcessEvent> doEvent(DSLContext tx, List<NewProcessEvent> events) {
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProcessEvent> insertedEvents = eventDao.insert(tx, events);

        eventsReceived.mark(events.size());
        batchInsertHistogram.update(events.size());

        return insertedEvents;
    }
}
