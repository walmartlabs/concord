package com.walmartlabs.concord.server.events.externalevent;

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

import com.walmartlabs.concord.server.events.DefaultEventFilter;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ExternalEventTriggerProcessor {

    private final TriggersDao dao;
    private final int version;

    @Inject
    public ExternalEventTriggerProcessor(TriggersDao dao, int version) {
        this.dao = dao;
        this.version = version;
    }

    public void process(String eventName, Map<String, Object> event, List<Result> result) {
        List<TriggerEntry> triggers = listTriggers(eventName);

        Map<String, Object> updatedEvent = buildEvent(event);

        for (TriggerEntry t : triggers) {
            if (DefaultEventFilter.filter(updatedEvent, t)) {
                result.add(Result.from(updatedEvent, t));
            }
        }
    }

    private Map<String, Object> buildEvent(Map<String, Object> event) {
        Map<String, Object> m = new HashMap<>(event);
        m.put("version", version);
        return m;
    }

    private List<TriggerEntry> listTriggers(String eventName) {
        return dao.list(eventName, version, null);
    }

    public static class Result {

        private final Map<String, Object> event;

        private final List<TriggerEntry> triggers;

        public Result(Map<String, Object> event, List<TriggerEntry> triggers) {
            this.event = event;
            this.triggers = triggers;
        }

        public Map<String, Object> event() {
            return event;
        }

        public List<TriggerEntry> triggers() {
            return triggers;
        }

        public static Result from(Map<String, Object> event, TriggerEntry trigger) {
            return new Result(event, Collections.singletonList(trigger));
        }
    }
}
