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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ProcessEventRequest;
import com.walmartlabs.concord.client.ProcessEventsApi;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.common.ObjectTruncater;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.ProcessDefinitionUtils;
import com.walmartlabs.concord.runtime.v2.model.EventConfiguration;
import com.walmartlabs.concord.runtime.v2.model.Location;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallEvent;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

public class TaskCallEventRecordingListener implements TaskCallListener {

    private static final Logger log = LoggerFactory.getLogger(TaskCallEventRecordingListener.class);

    private final ProcessEventsApi eventsApi;
    private final InstanceId processInstanceId;
    private final EventConfiguration eventConfiguration;

    @Inject
    public TaskCallEventRecordingListener(ApiClient apiClient, InstanceId processInstanceId, ProcessConfiguration processConfiguration) {
        this.eventsApi = new ProcessEventsApi(apiClient);
        this.processInstanceId = processInstanceId;
        this.eventConfiguration = processConfiguration.events();
    }

    @Override
    public void onEvent(TaskCallEvent event) {
        Map<String, Object> m = event(event);

        m.put("phase", event.phase().name().toLowerCase());

        List<Object> inVars = event.input();
        if (inVars != null && eventConfiguration.recordTaskInVars()) {
            Map<String, Object> vars = maskVars(convertInput(inVars), eventConfiguration.inVarsBlacklist());
            if (eventConfiguration.truncateInVars()) {
                vars = ObjectTruncater.truncateMap(vars, eventConfiguration.truncateMaxStringLength(), eventConfiguration.truncateMaxArrayLength(), eventConfiguration.truncateMaxDepth());
            }
            if (vars != null && !vars.isEmpty()) {
                m.put("in", vars);
            }
        }

        Object outVars = event.result();
        if (outVars != null && eventConfiguration.recordTaskOutVars()) {
            Map<String, Object> vars = maskVars(asMapOrNull(outVars), eventConfiguration.outVarsBlacklist());
            if (eventConfiguration.truncateOutVars()) {
                vars = ObjectTruncater.truncateMap(vars, eventConfiguration.truncateMaxStringLength(), eventConfiguration.truncateMaxArrayLength(), eventConfiguration.truncateMaxDepth());
            }
            if (vars != null && !vars.isEmpty()) {
                m.put("out", vars);
            }
        }

        if (event.duration() != null) {
            m.put("duration", event.duration());
        }

        send(m);
    }

    private static Map<String, Object> event(TaskCallEvent event) {
        Map<String, Object> m = new HashMap<>();

        Step currentStep = event.currentStep();
        m.put("processDefinitionId", ProcessDefinitionUtils.getCurrentFlowName(event.processDefinition(), currentStep));
        Location loc = currentStep != null ? currentStep.getLocation() : null;
        if (loc != null) {
            m.put("fileName", currentStep.getLocation().fileName());
            m.put("line", currentStep.getLocation().lineNum());
            m.put("column", currentStep.getLocation().column());
        }

        String taskName = event.taskName();
        m.put("description", "Task: " + taskName);
        m.put("name", taskName);

        m.put("correlationId", event.correlationId());

        return m;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMapOrNull(Object v) {
        if (v instanceof TaskResult.SimpleResult) {
            return ((TaskResult.SimpleResult) v).toMap();
        }

        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }

        return null;
    }

    static Map<String, Object> maskVars(Map<String, Object> vars, Collection<String> blackList) {
        if (blackList.isEmpty()) {
            return vars;
        }

        Map<String, Object> result = new HashMap<>(vars);
        for (String b : blackList) {
            String[] path = b.split("\\.");
            if (ConfigurationUtils.has(result, path)) {
                Map<String, Object> m = ensureModifiable(result, path.length - 1, path);
                m.put(path[path.length - 1], "***");
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ensureModifiable(Map<String, Object> m, int depth, String[] path) {
        if (depth == 0) {
            return m;
        }

        for (int i = 0; i < depth; i++) {
            Object v = m.get(path[i]);
            if (v == null) {
                throw new IllegalStateException("Can't find variable at " + path[i]);
            }

            if (!(v instanceof Map)) {
                throw new IllegalStateException("Not a map variable at " + path[i]);
            }

            Map<String, Object> modifiable = new HashMap<>((Map<String, Object>) v);
            m.put(path[i], modifiable);
            m = modifiable;
        }

        return m;
    }

    private static Map<String, Object> convertInput(List<Object> input) {
        if (input.isEmpty()) {
            return Collections.emptyMap();
        }

        if (input.size() == 1) {
            if (input.get(0) instanceof Variables) {
                return ((Variables) input.get(0)).toMap();
            }
        }

        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < input.size(); i++) {
            Object arg = input.get(i);
            if (arg instanceof Context) {
                arg = "context";
            }
            result.put(String.valueOf(i), arg);
        }

        return result;
    }
}
