package com.walmartlabs.concord.server.plugins.ansible;

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

import org.immutables.value.Value;
import org.jooq.DSLContext;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface EventProcessor {

    @Value.Immutable
    interface Event extends AbstractEventProcessor.Event {

        UUID instanceId();

        Timestamp instanceCreatedAt();

        Timestamp eventDate();

        String eventType();

        long eventSeq();

        Map<String, Object> payload();
    }

    void process(DSLContext tx, List<Event> events);
}
