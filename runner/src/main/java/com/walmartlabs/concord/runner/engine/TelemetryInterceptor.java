package com.walmartlabs.concord.runner.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

public class TelemetryInterceptor extends ExecutionInterceptorAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelemetryInterceptor.class);

    private static final int EVENT_TYPE = 0;

    private final RpcClient rpc;

    private final ProcessDefinitionProvider processDefinitionProvider;

    public TelemetryInterceptor(RpcClient rpc, ProcessDefinitionProvider processDefinitionProvider) {
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

        try {
            TelemetryEvent event = new TelemetryEvent(source.getLine(), source.getColumn(), source.getDescription());
            rpc.getEventService().onEvent(new Date(), EVENT_TYPE, event);
        } catch (Exception e) {
            log.warn("onElement ['{}'] -> telemetry transfer error", ev.getProcessBusinessKey(), e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class TelemetryEvent implements Serializable {

        private final int line;
        private final int column;
        private final String description;

        @JsonCreator
        public TelemetryEvent(
                @JsonProperty("line") int line,
                @JsonProperty("column") int column,
                @JsonProperty("description") String description) {
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

        @Override
        public String toString() {
            return "TelemetryEvent{" +
                    "line=" + line +
                    ", column=" + column +
                    ", description='" + description + '\'' +
                    '}';
        }
    }
}
