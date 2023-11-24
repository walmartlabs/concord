package com.walmartlabs.concord.server.process.queue;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.process.event.NewProcessEvent;
import com.walmartlabs.concord.server.process.event.ProcessEventManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.util.Collections;

/**
 * Sends {@link EventType#PROCESS_STATUS} events to registered {@link com.walmartlabs.concord.server.sdk.events.ProcessEventListener}
 * instances.
 */
public class ExternalProcessListenerHandler implements ProcessStatusListener {

    private final ProcessEventManager eventManager;

    @Inject
    public ExternalProcessListenerHandler(ProcessEventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public void onStatusChange(DSLContext tx, ProcessKey processKey, ProcessStatus status) {
        eventManager.event(tx, NewProcessEvent.builder()
                .processKey(processKey)
                .eventType(EventType.PROCESS_STATUS.name())
                .data(Collections.singletonMap("status", status.name()))
                .build());
    }
}
