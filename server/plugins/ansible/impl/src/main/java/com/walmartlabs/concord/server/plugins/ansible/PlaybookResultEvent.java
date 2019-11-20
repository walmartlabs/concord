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

import com.walmartlabs.concord.sdk.MapUtils;

import java.util.UUID;

public class PlaybookResultEvent {

    public static PlaybookResultEvent from(EventProcessor.Event e) {
        if (!e.eventType().equals(Constants.ANSIBLE_PLAYBOOK_RESULT)) {
            return null;
        }

        return new PlaybookResultEvent(e);
    }

    private final EventProcessor.Event e;

    private PlaybookResultEvent(EventProcessor.Event e) {
        this.e = e;
    }

    public UUID getPlaybookId() {
        return MapUtils.assertUUID(e.payload(), "parentCorrelationId");
    }

    public String getStatus() {
        return MapUtils.assertString(e.payload(), "status");
    }
}
