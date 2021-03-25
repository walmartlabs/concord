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
import java.util.Map;

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

    // TODO: remove me in the next release
    public void updateWaitOld(ProcessKey processKey, AbstractWaitCondition wait) {
        processWaitDao.tx(tx -> updateWaitOld(tx, processKey, wait));
    }

    // TODO: remove me in the next release
    public void updateWaitOld(DSLContext tx, ProcessKey processKey, AbstractWaitCondition wait) {
        processWaitDao.updateWaitOld(tx, processKey, wait);

        Map<String, Object> eventData = objectMapper.convertToMap(wait != null ? wait : new NoneCondition());
        NewProcessEvent e = NewProcessEvent.builder()
                .processKey(processKey)
                .eventType(EventType.PROCESS_WAIT.name())
                .data(eventData)
                .build();
        eventManager.event(tx, Collections.singletonList(e));
    }

    /**
     * @see #updateWait(DSLContext, ProcessKey, AbstractWaitCondition)
     */
    public void updateWait(ProcessKey processKey, AbstractWaitCondition wait) {
        processWaitDao.tx(tx -> updateWait(tx, processKey, wait));
    }

    /**
     * Updates the process' wait conditions. Adds a wait condition history event.
     */
    public void updateWait(DSLContext tx, ProcessKey processKey, AbstractWaitCondition wait) {
        processWaitDao.updateWait(tx, processKey, wait);

        Map<String, Object> eventData = objectMapper.convertToMap(wait != null ? wait : new NoneCondition());
        NewProcessEvent e = NewProcessEvent.builder()
                .processKey(processKey)
                .eventType(EventType.PROCESS_WAIT.name())
                .data(eventData)
                .build();
        eventManager.event(tx, Collections.singletonList(e));
    }
}
