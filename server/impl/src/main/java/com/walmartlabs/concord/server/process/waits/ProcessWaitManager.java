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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessWaitEntry;
import com.walmartlabs.concord.server.process.event.NewProcessEvent;
import com.walmartlabs.concord.server.process.event.ProcessEventManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public void tx(AbstractDao.Tx t) {
        processWaitDao.tx(t);
    }

    public <T> T txResult(AbstractDao.TxResult<T> t) {
        return processWaitDao.txResult(t);
    }

    public ProcessWaitEntry getWait(ProcessKey processKey) {
        return processWaitDao.get(processKey);
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

        eventManager.event(tx, Collections.singletonList(buildEvent(processKey, Collections.singletonList(wait), "add")));
    }

    /**
     * Set the process' wait conditions. Adds a wait condition history event.
     */
    public boolean setWait(DSLContext tx, ProcessKey processKey, List<AbstractWaitCondition> waits, boolean isWaiting, long version) {
        boolean updated = processWaitDao.setWait(tx, processKey, waits, isWaiting, version);
        if (updated) {
            eventManager.event(tx, buildEvent(processKey, waits, "set"));
        }
        return updated;
    }

    private NewProcessEvent buildEvent(ProcessKey processKey, List<AbstractWaitCondition> waits, String action) {
        Map<String, Object> data = new HashMap<>();
        if (waits != null && !waits.isEmpty()) {
            data.put("waits", waits.stream()
                    .map(objectMapper::convertToMap)
                    .collect(Collectors.toList()));
        }
        data.put("action", action);
        return NewProcessEvent.builder()
                .processKey(processKey)
                .eventType(EventType.PROCESS_WAIT.name())
                .data(data)
                .build();
    }

}
