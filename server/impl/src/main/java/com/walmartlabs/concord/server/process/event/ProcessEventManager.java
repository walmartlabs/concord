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
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.Listeners;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.events.ProcessEvent;
import com.walmartlabs.concord.server.sdk.metrics.InjectMeter;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Named
@Singleton
public class ProcessEventManager {

    private final ProcessEventDao eventDao;
    private final ConcordObjectMapper objectMapper;
    private final Listeners listeners;

    private final Histogram batchInsertHistogram;

    @InjectMeter
    private final Meter eventsReceived;

    @Inject
    public ProcessEventManager(ProcessEventDao eventDao,
                               ConcordObjectMapper objectMapper,
                               Listeners listeners,
                               Meter eventsReceived,
                               MetricRegistry metricRegistry) {

        this.eventDao = eventDao;
        this.objectMapper = objectMapper;
        this.listeners = listeners;
        this.eventsReceived = eventsReceived;

        this.batchInsertHistogram = metricRegistry.histogram("process-events-batch-insert");
    }

    public void insertStatusHistory(DSLContext tx, ProcessKey processKey, ProcessStatus status, Map<String, Object> statusPayload) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());
        payload.putAll(statusPayload);

        ProcessEvent e = new ProcessEvent(processKey, EventType.PROCESS_STATUS.name(), null, objectMapper.convertToMap(payload));
        event(tx, Collections.singletonList(e));
    }

    public void insertStatusHistory(DSLContext tx, List<ProcessKey> processKeys, ProcessStatus status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());

        List<ProcessEvent> events = processKeys.stream()
                .map(k -> new ProcessEvent(k, EventType.PROCESS_STATUS.name(), null, objectMapper.convertToMap(payload)))
                .collect(Collectors.toList());

        event(tx, events);
    }

    public void event(List<ProcessEvent> events) {
        eventDao.tx(tx -> event(tx, events));
    }

    @WithTimer
    public void event(DSLContext tx, List<ProcessEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        eventDao.insert(tx, events);

        eventsReceived.mark(events.size());
        batchInsertHistogram.update(events.size());

        listeners.onProcessEvent(events);
    }
}
