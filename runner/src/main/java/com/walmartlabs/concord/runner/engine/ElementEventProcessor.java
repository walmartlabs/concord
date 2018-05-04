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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.sdk.RpcClient;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.ProcessDefinitionUtils;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.model.AbstractElement;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.SourceAwareProcessDefinition;
import io.takari.bpm.model.SourceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.function.Predicate;

public class ElementEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(ElementEventProcessor.class);

    private final RpcClient rpc;

    private final ProcessDefinitionProvider processDefinitionProvider;

    public ElementEventProcessor(RpcClient rpc, ProcessDefinitionProvider processDefinitionProvider) {
        this.rpc = rpc;
        this.processDefinitionProvider = processDefinitionProvider;
    }

    public void process(String instanceId, String definitionId, String elementId, EventParamsBuilder builder) throws ExecutionException {
        process(instanceId, definitionId, elementId, builder, null);
    }

    public void process(String instanceId, String definitionId, String elementId,
                        EventParamsBuilder builder, Predicate<AbstractElement> filter) throws ExecutionException {
        ProcessDefinition pd = processDefinitionProvider.getById(definitionId);
        if(pd == null) {
            throw new RuntimeException("can't find process definition '" + definitionId + "'");
        }

        if(!(pd instanceof SourceAwareProcessDefinition)) {
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
            ProcessElementEvent e = new ProcessElementEvent(definitionId, elementId,
                    source.getLine(), source.getColumn(), source.getDescription(),
                    builder.build(element));

            rpc.getEventService().onEvent(instanceId, new Date(), EventType.PROCESS_ELEMENT, e);

        } catch (Exception e) {
            log.warn("process ['{}'] -> transfer error", instanceId, e);
        }
    }

    public interface EventParamsBuilder {
        Map<String, Object> build(AbstractElement element);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessElementEvent implements Serializable {

        private final String processDefinitionId;
        private final String elementId;
        private final int line;
        private final int column;
        private final String description;
        private final Map<String, Object> params;

        public ProcessElementEvent(
                String processDefinitionId, String elementId, int line, int column, String description,
                Map<String, Object> params) {
            this.processDefinitionId = processDefinitionId;
            this.elementId = elementId;
            this.line = line;
            this.column = column;
            this.description = description;
            this.params = params;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }

        public String getDescription() {
            return description;
        }

        public String getElementId() {
            return elementId;
        }

        public String getProcessDefinitionId() {
            return processDefinitionId;
        }

        @JsonAnyGetter
        public Map<String, Object> getParams() {
            return params;
        }
    }
}
