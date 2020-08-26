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

public class AnsibleEvent extends AbstractAnsibleEvent {

    public static AnsibleEvent from(EventProcessor.Event e) {
        if (!e.eventType().equals(Constants.ANSIBLE_EVENT_TYPE)) {
            return null;
        }

        return new AnsibleEvent(e);
    }

    private AnsibleEvent(EventProcessor.Event e) {
        super(e);
    }

    public String host() {
        return MapUtils.assertString(e.payload(), "host");
    }

    public String hostGroup() {
        return MapUtils.getString(e.payload(), "hostGroup", "-");
    }

    public UUID getPlayId() {
        return MapUtils.getUUID(e.payload(), "playId");
    }

    public String getStatus() {
        return MapUtils.getString(e.payload(), "status");
    }

    public UUID getTaskId() {
        return MapUtils.getUUID(e.payload(), "taskId");
    }

    public String getTaskName() {
        return MapUtils.assertString(e.payload(), "task");
    }

    public String getAction() {
        return MapUtils.getString(e.payload(), "action");
    }

    public boolean isSetupTask() {
        return "gather_facts".equals(getAction()) || "setup".equals(getAction());
    }

    public boolean isHandler() {
        return MapUtils.getBoolean(e.payload(), "isHandler", false);
    }

    public long duration() {
        return MapUtils.getNumber(e.payload(), "duration", 0L).longValue();
    }

    public boolean ignoreErrors() {
        Object value = e.payload().get("ignore_errors");
        if (value instanceof Boolean) {
            return (boolean) value;
        }
        return false;
    }
}
