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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runner.ContextUtils;
import com.walmartlabs.concord.sdk.Context;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.model.AbstractElement;
import io.takari.bpm.model.ServiceTask;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.project.InternalConstants.Context.EVENT_CORRELATION_KEY;

public class TaskEventInterceptor implements TaskInterceptor {

    private final ElementEventProcessor eventProcessor;
    private final ObjectMapper objectMapper;

    public TaskEventInterceptor(ElementEventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void preTask(String taskName, Context ctx) throws ExecutionException {
        tag("pre", taskName);

        UUID correlationId = UUID.randomUUID();

        eventProcessor.process(buildEvent(ctx), (element) -> {
            Map<String, Object> params = new HashMap<>();
            params.put("name", taskName);
            params.put("correlationId", correlationId);
            params.put("phase", "pre");
            List<VariableMapping> p = getInParams(ctx, element);
            if (p != null) {
                params.put("in", p);
            }
            return params;
        });

        ctx.setVariable(EVENT_CORRELATION_KEY, correlationId);
    }

    @Override
    public void postTask(String taskName, Context ctx) throws ExecutionException {
        tag("post", taskName);

        UUID correlationId = (UUID) ctx.getVariable(EVENT_CORRELATION_KEY);

        eventProcessor.process(buildEvent(ctx), (element) -> {
            Map<String, Object> params = new HashMap<>();
            params.put("name", taskName);
            params.put("correlationId", correlationId);
            params.put("phase", "post");
            List<VariableMapping> p = getOutParams(ctx, element);
            if (p != null) {
                params.put("out", p);
            }
            return params;
        });

        ctx.removeVariable(EVENT_CORRELATION_KEY);
    }

    private void tag(String phase, String taskName) throws ExecutionException {
        try {
            System.out.print("__logTag:");
            System.out.println(objectMapper.writeValueAsString(new TaskTag(phase, taskName)));
        } catch (IOException e) {
            throw new ExecutionException("Error while writing the task's tag: (" + phase + ", " + taskName + ")", e);
        }
    }

    private static ElementEventProcessor.ElementEvent buildEvent(Context ctx) {
        String instanceId = (String) ctx.getVariable(ExecutionContext.PROCESS_BUSINESS_KEY);

        return new ElementEventProcessor.ElementEvent(instanceId,
                ctx.getProcessDefinitionId(), ctx.getElementId(), ContextUtils.getSessionToken(ctx));
    }

    private static List<VariableMapping> getInParams(Context ctx, AbstractElement element) {
        if (!(element instanceof ServiceTask)) {
            return null;
        }

        ServiceTask t = (ServiceTask) element;
        if (t.getIn() == null) {
            return null;
        }

        return convertParams(ctx, t.getIn());
    }

    private static List<VariableMapping> getOutParams(Context ctx, AbstractElement element) {
        if (!(element instanceof ServiceTask)) {
            return null;
        }

        ServiceTask t = (ServiceTask) element;
        if (t.getIn() == null) {
            return null;
        }

        return convertParams(ctx, t.getOut());
    }

    private static List<VariableMapping> convertParams(Context ctx, Collection<io.takari.bpm.model.VariableMapping> m) {
        if (m == null) {
            return null;
        }

        return m.stream()
                .map(v -> toMapping(ctx, v))
                .collect(Collectors.toList());
    }

    private static VariableMapping toMapping(Context ctx, io.takari.bpm.model.VariableMapping v) {
        Serializable resolved = null;

        Object o = ctx.getVariable(v.getTarget());
        if (o instanceof Serializable) {
            resolved = (Serializable) o;
        }

        return new VariableMapping(v.getSource(), v.getSourceExpression(), v.getSourceValue(), v.getTarget(), resolved);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VariableMapping implements Serializable {

        private final String source;
        private final String sourceExpression;
        private final Object sourceValue;
        private final String target;
        private final Serializable resolved;

        @JsonCreator
        public VariableMapping(
                @JsonProperty("source") String source,
                @JsonProperty("sourceExpression") String sourceExpression,
                @JsonProperty("sourceValue") Object sourceValue,
                @JsonProperty("target") String target,
                @JsonProperty("resolved") Serializable resolved) {

            this.source = source;
            this.sourceExpression = sourceExpression;
            this.sourceValue = sourceValue;
            this.target = target;
            this.resolved = resolved;
        }

        public String getSource() {
            return source;
        }

        public String getSourceExpression() {
            return sourceExpression;
        }

        public Object getSourceValue() {
            return sourceValue;
        }

        public String getTarget() {
            return target;
        }

        public Serializable getResolved() {
            return resolved;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private static final class TaskTag implements Serializable {

        private final String phase;
        private final String taskName;

        private TaskTag(String phase, String taskName) {
            this.phase = phase;
            this.taskName = taskName;
        }

        public String getPhase() {
            return phase;
        }

        public String getTaskName() {
            return taskName;
        }
    }
}
