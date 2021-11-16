package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.model.AbstractElement;

import java.util.Map;
import java.util.function.Predicate;

public interface ElementEventProcessor {

    interface EventParamsBuilder {

        Map<String, Object> build(AbstractElement element);
    }

    class ElementEvent {

        private final String instanceId;
        private final String processDefinitionId;
        private final String elementId;
        private final String sessionToken;

        public ElementEvent(String instanceId, String processDefinitionId, String elementId, String sessionToken) {
            this.instanceId = instanceId;
            this.processDefinitionId = processDefinitionId;
            this.elementId = elementId;
            this.sessionToken = sessionToken;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public String getProcessDefinitionId() {
            return processDefinitionId;
        }

        public String getElementId() {
            return elementId;
        }

        public String getSessionToken() {
            return sessionToken;
        }
    }

    void process(ElementEvent event, EventParamsBuilder builder) throws ExecutionException;

    void process(ElementEvent event, EventParamsBuilder builder, Predicate<AbstractElement> filter) throws ExecutionException;
}
