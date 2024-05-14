package com.walmartlabs.concord.runtime.v2.runner.context;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.walmartlabs.concord.runtime.v2.sdk.ResumeEvent;

import java.io.Serializable;
import java.util.Map;

@JsonPropertyOrder({"eventName", "state"})
public class ResumeEventImpl implements ResumeEvent {

    // for backward compatibility (java8 concord 1.92.0 version)
    private static final long serialVersionUID = -8446483662158789554L;

    private final String eventName;

    private final Map<String, Serializable> state;

    public ResumeEventImpl(String eventName, Map<String, Serializable> state) {
        this.eventName = eventName;
        this.state = state;
    }

    @Override
    @JsonProperty("eventName")
    public String eventName() {
        return eventName;
    }

    @Override
    @JsonProperty("state")
    public Map<String, Serializable> state() {
        return state;
    }

    @Override
    public String toString() {
        return "ResumeEventImpl{" +
                "eventName='" + eventName + '\'' +
                ", state=" + state +
                '}';
    }
}
