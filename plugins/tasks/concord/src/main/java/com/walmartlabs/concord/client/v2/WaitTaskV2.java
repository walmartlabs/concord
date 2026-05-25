package com.walmartlabs.concord.client.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ClientUtils;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.ReentrantTask;
import com.walmartlabs.concord.runtime.v2.sdk.ResumeEvent;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Named("waitForExternalEvent")
public class WaitTaskV2 implements ReentrantTask {

    private static final Logger log = LoggerFactory.getLogger(WaitTaskV2.class);

    private final Context context;
    private final UUID txId;
    private final ApiClient apiClient;
    private final ObjectMapperProvider objectMapperProvider;

    @Inject
    public WaitTaskV2(Context context, ApiClient apiClient, ObjectMapperProvider objectMapperProvider) {
        this.context = context;
        this.txId = context.processInstanceId();
        this.apiClient = apiClient;
        this.objectMapperProvider = objectMapperProvider;
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        var mapper = objectMapperProvider.get();

        List<EventConfig> externalEvents = mapper.convertValue(input.assertList("externalEvents"), mapper.getTypeFactory().constructCollectionType(List.class, EventConfig.class));

        warnVariableOverwrite(externalEvents);

        if (externalEvents.size() > 10) {
            throw new IllegalArgumentException("Too many (>10) external events supplied.");
        }
        var externalEventIds = externalEvents.stream()
                .map(EventConfig::eventId)
                .filter(Objects::nonNull)
                .toList();

        log.info("Creating {} external wait conditions.", externalEventIds.size());

        var resumeEvent = "concord_wait_resume_" + UUID.randomUUID();
        var resumeExamples = new StringBuilder();

        for (EventConfig externalEvent : externalEvents) {
            resumeExamples.append(String.format("""
                            curl -n -X POST \\
                            -H "content-type: application/json" \\
                            -d '{ "newVar": "hello new var for %s" }' \\
                            "%s/api/v1/process/%s/external-wait/%s/clear"
                            """,
                    externalEvent.eventId(), apiClient.getBaseUrl(), txId, externalEvent.eventId()));

            Map<String, Object> condition = new HashMap<>();
            condition.put("type", "EXTERNAL_EVENT");
            condition.put("reason", "Waiting on external event: " + externalEvent.eventId());
            condition.put("waiting", true);
            condition.put("externalEvent", externalEvent.eventId());
            condition.put("saveAs", resumeEvent + "." + externalEvent.saveAs());
            condition.put("resumeEvent", resumeEvent);

            ClientUtils.withRetry(3, 1000, () -> {
                ProcessApi api = new ProcessApi(apiClient);
                api.setWaitCondition(txId, condition);
                return null;
            });
        }

        log.info("Resume with:\n{}", resumeExamples);

        // serialize necessary state data to handle resume
        var state = new ExternalResumeState(externalEvents);
        var mapOfSerializableType = mapper.getTypeFactory()
                .constructMapType(Map.class, String.class, Serializable.class);

        return TaskResult.reentrantSuspend(resumeEvent, mapper.convertValue(state, mapOfSerializableType));
    }

    /**
     * Warns if more than one event has the same {@code saveAs} value.
     */
    private static void warnVariableOverwrite(List<EventConfig> eventConfigs) {
        Set<String> allVars = new HashSet<>();

        eventConfigs.stream()
                .map(EventConfig::saveAs)
                .filter(eventId -> !allVars.add(eventId))
                .forEach(duplicateVarName -> log.warn("Duplicate resume variable '{}' supplied. This may result in overwritten results.", duplicateVarName));
    }

    @Override
    public TaskResult resume(ResumeEvent event) throws Exception {
        var mapper = objectMapperProvider.get();
        var resumeState = mapper.convertValue(event.state(), ExternalResumeState.class);

        var data = new HashMap<String, Object>();

        log.info("all vars: {}", mapper.writeValueAsString(context.variables().toMap()));


        var resumeVars = context.variables().getMap(event.eventName(), Map.of());


        // TODO Remove this debug logging
        resumeState.externalEvents().forEach(eventConfig -> {
            var expectedVar = eventConfig.saveAs();
            var value = resumeVars.get(expectedVar);
            log.info("new var '{}': {}", expectedVar, value);
        });


        // Move the vars from the global context to the task's output.
        for (var eventConfig : resumeState.externalEvents()) {
            data.put(eventConfig.saveAs(), resumeVars.getOrDefault(eventConfig.saveAs(), null));
        }

        context.variables().set(event.eventName(), null); // clean up global context vars.

        return TaskResult.success().values(data);
    }

    record EventConfig(String eventId, String saveAs) {
    }

    record ExternalResumeState(List<EventConfig> externalEvents) {
    }

}
