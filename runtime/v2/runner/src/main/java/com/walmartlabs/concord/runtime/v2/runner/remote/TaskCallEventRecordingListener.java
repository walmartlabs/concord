package com.walmartlabs.concord.runtime.v2.runner.remote;

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

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ProcessEventRequest;
import com.walmartlabs.concord.client.ProcessEventsApi;
import com.walmartlabs.concord.runtime.common.cfg.EventConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.InstanceId;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallEvent;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskCallEventRecordingListener implements TaskCallListener {

    private static final Logger log = LoggerFactory.getLogger(TaskCallEventRecordingListener.class);

    private final ProcessEventsApi eventsApi;
    private final InstanceId processInstanceId;
    private final EventConfiguration eventConfiguration;

    @Inject
    public TaskCallEventRecordingListener(ApiClient apiClient, InstanceId processInstanceId, RunnerConfiguration runnerConfiguration) {
        this.eventsApi = new ProcessEventsApi(apiClient);
        this.processInstanceId = processInstanceId;
        this.eventConfiguration = runnerConfiguration.events();
    }

    @Override
    public void onEvent(TaskCallEvent event) {
        Map<String, Object> m = event(event);

        m.put("phase", event.phase().name().toLowerCase());

        if (event.input() != null && eventConfiguration.recordTaskInVars()) {
            m.put("in", maskVars(event.input(), eventConfiguration.inVarsBlacklist()));
        }

        if (event.out() != null && eventConfiguration.recordTaskOutVars()) {
            m.put("out", maskVars(event.out(), eventConfiguration.outVarsBlacklist()));
        }

        if (event.duration() != null) {
            m.put("duration", event.duration());
        }

        send(m);
    }

    private static Map<String, Object> event(TaskCallEvent event) {
        Map<String, Object> m = new HashMap<>();

        Step currentStep = event.currentStep();
        m.put("processDefinitionId", getCurrentFlowName(event.processDefinition(), currentStep));
        JsonLocation loc = currentStep != null ? currentStep.getLocation() : null;
        if (loc != null) {
            m.put("line", currentStep.getLocation().getLineNr());
            m.put("column", currentStep.getLocation().getColumnNr());
        }

        String taskName = event.taskName();
        m.put("description", "Task: " + taskName);
        m.put("name", taskName);

        m.put("correlationId", event.correlationId());

        return m;
    }

    private static String getCurrentFlowName(ProcessDefinition processDefinition, Step currentStep) {
        if (currentStep == null) {
            return null;
        }

        for (Map.Entry<String, List<Step>> e : processDefinition.flows().entrySet()) {
            if (e.getValue().contains(currentStep)) {
                return e.getKey();
            }
        }

        return null;
    }

    private void send(Map<String, Object> event) {
        ProcessEventRequest req = new ProcessEventRequest();
        req.setEventType("ELEMENT"); // TODO should it be in the constants?
        req.setData(event);
        req.setEventDate(Instant.now().atOffset(ZoneOffset.UTC));

        try {
            eventsApi.event(processInstanceId.getValue(), req);
        } catch (ApiException e) {
            log.warn("send [{}] -> error while sending an event to the server: {}", event, e.getMessage());
        }
    }

    private static Object maskVars(Map<String, Object> vars, Collection<String> blackList) {
        if (blackList.isEmpty()) {
            return vars;
        }

        Map<String, Object> result = new HashMap<>(vars);
        for (String b : blackList) {
            if (result.containsKey(b)) {
                result.put(b, "***");
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object maskVars(Object vars, Collection<String> blackList) {
        if (!(vars instanceof Map)) {
            return vars;
        }

        Map<String, Object> v = (Map<String, Object>) vars;
        return maskVars(v, blackList);
    }
}
