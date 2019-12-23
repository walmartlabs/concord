package com.walmartlabs.concord.server.plugins.eventsink.kafka;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.audit.AuditEvent;
import com.walmartlabs.concord.server.sdk.audit.AuditLogListener;
import com.walmartlabs.concord.server.sdk.events.ProcessEvent;
import com.walmartlabs.concord.server.sdk.events.ProcessEventListener;
import com.walmartlabs.concord.server.sdk.log.ProcessLogListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Named
public class KafkaEventSink implements ProcessEventListener, ProcessLogListener, AuditLogListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventSink.class);

    private final KafkaEventSinkConfiguration cfg;
    private final KafkaConnector connector;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public KafkaEventSink(KafkaEventSinkConfiguration cfg, KafkaConnector connector) {
        this.cfg = cfg;
        this.connector = connector;
    }

    @Override
    public void onEvent(AuditEvent event) {
        UUID k = event.getUserId();
        try {
            String v = objectMapper.writeValueAsString(event);
            connector.send(cfg.getAuditLogTopic(), k, v);
        } catch (Exception e) {
            log.warn("onEvent [{}] -> error while sending an audit log event: {}", k, e.getMessage());
        }
    }

    @Override
    public void onEvents(List<ProcessEvent> events) {
        for (ProcessEvent ev : events) {
            ProcessKey k = ev.getProcessKey();
            try {
                String v = objectMapper.writeValueAsString(ev);
                connector.send(cfg.getProcessEventsTopic(), k.getInstanceId(), v);
            } catch (Exception e) {
                log.warn("onEvents [{}] -> error while sending an event: {}", k, e.getMessage());
            }
        }
    }

    @Override
    public void onAppend(ProcessKey processKey, byte[] msg) {
        UUID k = processKey.getInstanceId();
        try {
            String v = objectMapper.writeValueAsString(Collections.singletonMap("msg", new String(msg)));
            connector.send(cfg.getProcessLogsTopic(), k, v);
        } catch (Exception e) {
            log.warn("onAppend [{}] -> error while sending a log entry: {}", k, e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "KafkaEventSink -> " + cfg.getBootstrapServers();
    }
}
