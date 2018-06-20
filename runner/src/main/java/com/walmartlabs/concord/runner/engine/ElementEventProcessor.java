package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.client.ProcessEventRequest;
import com.walmartlabs.concord.client.ProcessEventsApi;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.ProcessDefinitionUtils;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.model.AbstractElement;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.SourceAwareProcessDefinition;
import io.takari.bpm.model.SourceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class ElementEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(ElementEventProcessor.class);

    private final ProcessEventsApi eventsApi;
    private final ProcessDefinitionProvider processDefinitionProvider;

    public ElementEventProcessor(ProcessEventsApi eventsApi, ProcessDefinitionProvider processDefinitionProvider) {
        this.eventsApi = eventsApi;
        this.processDefinitionProvider = processDefinitionProvider;
    }

    public void process(String instanceId, String definitionId, String elementId, EventParamsBuilder builder) throws ExecutionException {
        process(instanceId, definitionId, elementId, builder, null);
    }

    public void process(String instanceId, String definitionId, String elementId,
                        EventParamsBuilder builder, Predicate<AbstractElement> filter) throws ExecutionException {

        ProcessDefinition pd = processDefinitionProvider.getById(definitionId);
        if (pd == null) {
            throw new RuntimeException("can't find process definition '" + definitionId + "'");
        }

        if (!(pd instanceof SourceAwareProcessDefinition)) {
            return;
        }

        Map<String, SourceMap> sourceMaps = ((SourceAwareProcessDefinition) pd).getSourceMaps();

        SourceMap source = sourceMaps.get(elementId);
        if (source == null) {
            return;
        }

        AbstractElement element = ProcessDefinitionUtils.findElement(pd, elementId);

        if (filter != null && !filter.test(element)) {
            return;
        }

        try {
            Map<String, Object> e = new HashMap<>();
            e.put("processDefinitionId", definitionId);
            e.put("elementId", elementId);
            e.put("line", source.getLine());
            e.put("column", source.getColumn());
            e.put("description", source.getDescription());
            e.putAll(builder.build(element));

            ProcessEventRequest req = new ProcessEventRequest();
            req.setEventType(ProcessEventRequest.EventTypeEnum.ELEMENT);
            req.setData(e);

            eventsApi.event(UUID.fromString(instanceId), req);

        } catch (Exception e) {
            log.warn("process ['{}'] -> transfer error: {}", instanceId, e.getMessage());
        }
    }

    public interface EventParamsBuilder {
        Map<String, Object> build(AbstractElement element);
    }
}
