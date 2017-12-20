package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.sdk.RpcClient;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.api.interceptors.ExecutionInterceptorAdapter;
import io.takari.bpm.api.interceptors.InterceptorElementEvent;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.SourceAwareProcessDefinition;
import io.takari.bpm.model.SourceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

public class ProcessElementInterceptor extends ExecutionInterceptorAdapter {

    private static final Logger log = LoggerFactory.getLogger(ProcessElementInterceptor.class);

    private final RpcClient rpc;

    private final ProcessDefinitionProvider processDefinitionProvider;

    public ProcessElementInterceptor(RpcClient rpc, ProcessDefinitionProvider processDefinitionProvider) {
        this.rpc = rpc;
        this.processDefinitionProvider = processDefinitionProvider;
    }

    @Override
    public void onElement(InterceptorElementEvent ev) throws ExecutionException {
        ProcessDefinition pd = processDefinitionProvider.getById(ev.getProcessDefinitionId());
        if(pd == null) {
            throw new RuntimeException("can't find process definition '" + ev.getProcessDefinitionId() + "'");
        }

        if(!(pd instanceof SourceAwareProcessDefinition)) {
            return;
        }

        Map<String, SourceMap> sourceMaps = ((SourceAwareProcessDefinition) pd).getSourceMaps();

        SourceMap source = sourceMaps.get(ev.getElementId());
        if(source == null) {
            return;
        }

        String instanceId = ev.getProcessBusinessKey();

        try {
            rpc.getEventService().onEvent(instanceId, new Date(), EventType.PROCESS_ELEMENT, convert(ev, source));
        } catch (Exception e) {
            log.warn("onElement ['{}'] -> transfer error", ev.getProcessBusinessKey(), e);
        }
    }

    private static ProcessElementEvent convert(InterceptorElementEvent ev, SourceMap source) {
        return new ProcessElementEvent(
                ev.getProcessDefinitionId(), ev.getElementId(),
                source.getLine(), source.getColumn(), source.getDescription());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ProcessElementEvent implements Serializable {

        private final String processDefinitionId;
        private final String elementId;
        private final int line;
        private final int column;
        private final String description;

        @JsonCreator
        public ProcessElementEvent(
                @JsonProperty("processDefinitionId") String processDefinitionId,
                @JsonProperty("elementId") String elementId,
                @JsonProperty("line") int line,
                @JsonProperty("column") int column,
                @JsonProperty("description") String description) {
            this.processDefinitionId = processDefinitionId;
            this.elementId = elementId;
            this.line = line;
            this.column = column;
            this.description = description;
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
    }
}
