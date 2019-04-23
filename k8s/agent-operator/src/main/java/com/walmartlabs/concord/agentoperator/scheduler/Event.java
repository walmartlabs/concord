package com.walmartlabs.concord.agentoperator.scheduler;

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

import com.walmartlabs.concord.agentoperator.crd.AgentPool;

public class Event {

    private final Type type;
    private final AgentPool resource;

    public Event(Type type, AgentPool resource) {
        this.type = type;
        this.resource = resource;
    }

    public Type getType() {
        return type;
    }

    public AgentPool getResource() {
        return resource;
    }

    public enum Type {

        MODIFIED,
        DELETED
    }
}
