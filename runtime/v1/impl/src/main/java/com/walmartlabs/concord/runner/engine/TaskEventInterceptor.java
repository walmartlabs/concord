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
import com.walmartlabs.concord.runtime.common.ObjectTruncater;
import com.walmartlabs.concord.sdk.ContextUtils;
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
import static com.walmartlabs.concord.project.InternalConstants.Context.EVENT_CREATED_AT_KEY;

public class TaskEventInterceptor implements TaskInterceptor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final EventConfiguration cfg;
    private final ElementEventProcessor eventProcessor;

    public TaskEventInterceptor(EventConfiguration cfg, ElementEventProcessor eventProcessor) {
        this.cfg = cfg;
        this.eventProcessor = eventProcessor;
    }

    @Override
    public void preTask(String taskName, Object instance, Context ctx) throws ExecutionException {
        UUID correlationId = UUID.randomUUID();

        Map<String, Object> logMetaData = getLogMetaData(instance, ctx);
        TaskTag.pre(taskName, instance, correlationId, logMetaData).log();

        eventProcessor.process(buildEvent(ctx), element -> {
            Map<String, Object> params = new HashMap<>();
            params.put("name", taskName);
            params.put("correlationId", correlationId);
            params.put("phase", "pre");
            getCheckpointName(ctx, element).ifPresent(n -> params.put("description", n));
            List<VariableMapping> p = getInParams(ctx, element);
            if (p != null) {
                params.put("in", p);
            }
            return params;
        });

        ctx.setVariable(EVENT_CORRELATION_KEY, correlationId);
        ctx.setVariable(EVENT_CREATED_AT_KEY, System.currentTimeMillis());
    }

    @Override
    public void postTask(String taskName, Object instance, Context ctx) throws ExecutionException {
        UUID correlationId = (UUID) ctx.getVariable(EVENT_CORRELATION_KEY);
        Long preEventTime = (Long) ctx.getVariable(EVENT_CREATED_AT_KEY);

        Map<String, Object> logMetaData = getLogMetaData(instance, ctx);
        TaskTag.post(taskName, instance, correlationId, logMetaData).log();

        eventProcessor.process(buildEvent(ctx), element -> {
            Map<String, Object> params = new HashMap<>();
            params.put("name", taskName);
            params.put("correlationId", correlationId);
            params.put("phase", "post");
            getCheckpointName(ctx, element)
                    .ifPresent(n -> params.put("description", n));
            List<VariableMapping> p = getOutParams(ctx, element);
            if (p != null) {
                params.put("out", p);
            }
            if (preEventTime != null) {
                params.put("duration", System.currentTimeMillis() - preEventTime);
            }
            if (logMetaData != null) {
                params.put("logMetaData", logMetaData);
            }
            return params;
        });

        ctx.removeVariable(EVENT_CORRELATION_KEY);
        ctx.removeVariable(EVENT_CREATED_AT_KEY);
    }

    private Map<String, Object> getLogMetaData(Object task, Context ctx) {
        Class<?> clazz = task.getClass();

        if (LogTagMetadataProvider.class.isAssignableFrom(clazz)) {
            LogTagMetadataProvider p = (LogTagMetadataProvider) task;
            return p.createLogTagMetadata(ctx);
        }

        return null;
    }

    private Optional<String> getCheckpointName(Context ctx, AbstractElement element) {
        if (!(element instanceof ServiceTask)) {
            return Optional.empty();
        }

        ServiceTask t = (ServiceTask) element;
        if (t.getIn() == null) {
            return Optional.empty();
        }

        if (!t.getExpression().equals("${checkpoint}")) {
            return Optional.empty();
        }

        return t.getIn().stream()
                .filter(v -> v.getTarget().equals("checkpointName"))
                .map(v -> {
                    String name = v.getSourceExpression();

                    if (cfg.isEvalCheckpointNames()) {
                        String evaluated = ContextUtils.getString(ctx, "checkpointName", name);
                        name = ObjectTruncater.truncate(evaluated, 128, 1, 1).toString();
                    }

                    return "Checkpoint: " + name;
                })
                .findAny();
    }

    private List<VariableMapping> getInParams(Context ctx, AbstractElement element) {
        if (!cfg.isRecordTaskInVars()) {
            return Collections.emptyList();
        }

        if (!(element instanceof ServiceTask)) {
            return null;
        }

        ServiceTask t = (ServiceTask) element;
        if (t.getIn() == null) {
            return null;
        }

        return convertParams(ctx, t.getIn(), cfg.getInVarsBlacklist(), cfg.isTruncateInVars(), cfg.getTruncateMaxStringLength(), cfg.getTruncateMaxArrayLength(), cfg.getTruncateMaxDepth());
    }

    private List<VariableMapping> getOutParams(Context ctx, AbstractElement element) {
        if (!cfg.isRecordTaskOutVars()) {
            return Collections.emptyList();
        }

        if (!(element instanceof ServiceTask)) {
            return null;
        }

        ServiceTask t = (ServiceTask) element;
        if (t.getOut() == null) {
            return null;
        }

        return convertParams(ctx, t.getOut(), cfg.getOutVarsBlacklist(), cfg.isTruncateOutVars(), cfg.getTruncateMaxStringLength(), cfg.getTruncateMaxArrayLength(), cfg.getTruncateMaxDepth());
    }

    private static ElementEventProcessor.ElementEvent buildEvent(Context ctx) {
        String instanceId = (String) ctx.getVariable(ExecutionContext.PROCESS_BUSINESS_KEY);

        return new ElementEventProcessor.ElementEvent(instanceId,
                ctx.getProcessDefinitionId(), ctx.getElementId(), ContextUtils.getSessionToken(ctx));
    }

    private static List<VariableMapping> convertParams(Context ctx,
                                                       Collection<io.takari.bpm.model.VariableMapping> m,
                                                       Collection<String> blacklist,
                                                       boolean truncate,
                                                       int maxStringLength, int maxArrayLength, int maxDepth) {
        if (m == null) {
            return null;
        }

        return m.stream()
                .map(v -> toMapping(ctx, v, blacklist.contains(v.getTarget()), truncate, maxStringLength, maxArrayLength, maxDepth))
                .collect(Collectors.toList());
    }

    private static VariableMapping toMapping(Context ctx,
                                             io.takari.bpm.model.VariableMapping v,
                                             boolean blacklisted,
                                             boolean truncate,
                                             int maxStringLength, int maxArrayLength, int maxDepth) {
        Serializable resolved = "n/a";

        Object o = ctx.getVariable(v.getTarget());
        if (!blacklisted && o instanceof Serializable) {
            resolved = (Serializable) o;

            if (truncate) {
                resolved = (Serializable)ObjectTruncater.truncate(resolved, maxStringLength, maxArrayLength, maxDepth);
            }
        }

        Object sourceValue = ObjectTruncater.truncate(v.getSourceValue(), maxStringLength, maxArrayLength, maxDepth);

        return new VariableMapping(v.getSource(), v.getSourceExpression(), sourceValue, v.getTarget(), resolved);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VariableMapping implements Serializable {

        private static final long serialVersionUID = 1L;

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

        private static final long serialVersionUID = 1L;

        public static TaskTag pre(String taskName, Object task, UUID correlationId, Map<String, Object> meta) {
            return new TaskTag("pre", taskName, correlationId, meta);
        }

        public static TaskTag post(String taskName, Object task, UUID correlationId, Map<String, Object> meta) {
            return new TaskTag("post", taskName, correlationId, meta);
        }

        private final String phase;
        private final String taskName;
        private final UUID correlationId;
        private final Map<String, Object> meta;

        private TaskTag(String phase, String taskName, UUID correlationId, Map<String, Object> meta) {
            this.phase = phase;
            this.taskName = taskName;
            this.correlationId = correlationId;
            this.meta = meta;
        }

        public String getPhase() {
            return phase;
        }

        public String getTaskName() {
            return taskName;
        }

        public UUID getCorrelationId() {
            return correlationId;
        }

        public Map<String, Object> getMeta() {
            return meta;
        }

        private void log() throws ExecutionException {
            try {
                System.out.print("__logTag:");
                System.out.println(objectMapper.writeValueAsString(this));
            } catch (IOException e) {
                throw new ExecutionException("Error while writing the task's tag: (" + phase + ", " + taskName + ")", e);
            }
        }
    }
}
