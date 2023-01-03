package com.walmartlabs.concord.server.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.sdk.MapUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

public abstract class AbstractAnsibleEvent {

    protected final EventProcessor.Event e;

    protected AbstractAnsibleEvent(EventProcessor.Event e) {
        this.e = e;
    }

    public UUID instanceId() {
        return e.instanceId();
    }

    public OffsetDateTime instanceCreatedAt() {
        return e.instanceCreatedAt();
    }

    public long eventSeq() {
        return e.eventSeq();
    }

    public UUID playbookId() {
        UUID result = MapUtils.getUUID(e.payload(), "playbookId");
        if (result != null) {
            return result;
        }
        result = MapUtils.getUUID(e.payload(), "parentCorrelationId");
        if (result != null) {
            return result;
        }
        return e.instanceId();
    }
}
