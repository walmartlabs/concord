package com.walmartlabs.concord.server.process.waits;

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
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.process.event.NewProcessEvent;
import com.walmartlabs.concord.server.process.event.ProcessEventManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
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
public class ProcessWaitManager {

    private final ProcessWaitDao processWaitDao;
    private final ConcordObjectMapper objectMapper;
    private final ProcessEventManager eventManager;

    @Inject
    public ProcessWaitManager(ProcessWaitDao processWaitDao, ConcordObjectMapper objectMapper, ProcessEventManager eventManager) {
        this.processWaitDao = processWaitDao;
        this.objectMapper = objectMapper;
        this.eventManager = eventManager;
    }

    // TODO: old process_queue.wait_conditions code, remove me (1.84.0 or later)
    public void updateWaitOld(ProcessKey processKey, AbstractWaitCondition wait) {
        processWaitDao.tx(tx -> updateWaitOld(tx, processKey, wait));
    }

    // TODO: old process_queue.wait_conditions code, remove me (1.84.0 or later)
    public void updateWaitOld(DSLContext tx, ProcessKey processKey, AbstractWaitCondition wait) {
        processWaitDao.updateWaitOld(tx, processKey, wait);
        addWaitEvent(tx, processKey, wait);
    }

    /**
     * @see #addWait(DSLContext, ProcessKey, AbstractWaitCondition)
     */
    public void addWait(ProcessKey processKey, AbstractWaitCondition wait) {
        processWaitDao.tx(tx -> addWait(tx, processKey, wait));
    }

    /**
     * Add the process' wait conditions. Adds a wait condition history event.
     */
    public void addWait(DSLContext tx, ProcessKey processKey, AbstractWaitCondition wait) {
        processWaitDao.addWait(tx, processKey, wait);
        addWaitEvent(tx, processKey, wait);
    }

    /**
     * Set the process' wait conditions. Adds a wait condition history event.
     */
    public void setWait(ProcessKey processKey, List<AbstractWaitCondition> waits) {
        processWaitDao.tx(tx -> setWait(tx, processKey, waits));
    }

    private void setWait(DSLContext tx, ProcessKey processKey, List<AbstractWaitCondition> waits) {
        processWaitDao.setWait(tx, processKey, waits);
        addWaitEvents(tx, processKey, waits);
    }

    private void addWaitEvent(DSLContext tx, ProcessKey processKey, AbstractWaitCondition wait) {
        eventManager.event(tx, Collections.singletonList(buildEvent(processKey, wait, "add")));
    }

    private void addWaitEvents(DSLContext tx, ProcessKey processKey, List<AbstractWaitCondition> waits) {
        if (waits == null || waits.isEmpty()) {
            return;
        }

        List<NewProcessEvent> events = waits.stream()
                .map(wait -> buildEvent(processKey, wait, "set"))
                .collect(Collectors.toList());

        eventManager.event(tx, events);
    }

    private NewProcessEvent buildEvent(ProcessKey processKey, AbstractWaitCondition wait, String action) {
        Map<String, Object> data = new HashMap<>();
        data.put("wait", objectMapper.convertToMap(wait));
        data.put("action", action);
        return NewProcessEvent.builder()
                .processKey(processKey)
                .eventType(EventType.PROCESS_WAIT.name())
                .data(data)
                .build();
    }
}
