package com.walmartlabs.concord.project.yaml.converter;

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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.project.yaml.KV;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlTaskStep;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.api.BpmnError;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.model.*;
import io.takari.parc.Seq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class YamlTaskStepConverter implements StepConverter<YamlTaskStep> {

    private static final long DEFAULT_DELAY = TimeUnit.SECONDS.toMillis(5);

    @Override
    public Chunk convert(ConverterContext ctx, YamlTaskStep s) throws YamlConverterException {
        Chunk c = new Chunk();

        Set<VariableMapping> inVars = getVarMap(s.getOptions(), "in");
        Set<VariableMapping> outVars = getVarMap(s.getOptions(), "out");

        String id = ctx.nextId();
        String expr = "${" + s.getKey() + "}";
        c.addElement(new ServiceTask(id, ExpressionType.DELEGATE, expr, inVars, outVars, true));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Task: " + s.getKey()));

        Map<String, Object> opts = s.getOptions();
        if (opts != null && opts.get("error") != null && opts.get("retry") != null) {
            throw new YamlConverterException("'error' and 'retry' options are mutually exclusive");
        }

        applyErrorBlock(ctx, c, id, s.getOptions());
        applyRetryBlock(ctx, c, id, s);

        return applyWithItems(ctx, c, s.getOptions());
    }

    @SuppressWarnings("unchecked")
    private void applyRetryBlock(ConverterContext ctx, Chunk c, String attachedRef, YamlTaskStep s) throws YamlConverterException {
        Map<String, Object> opts = s.getOptions();
        if (opts == null) {
            return;
        }

        Map<String, Object> retryParams = toMap((Seq<KV<String, Object>>) opts.get("retry"));
        if (retryParams.isEmpty()) {
            return;
        }

        // task boundary event
        String originalTaskEvId = ctx.nextId();
        c.addElement(new BoundaryEvent(originalTaskEvId, attachedRef, null));

        // inc retry count
        String incCounterId = ctx.nextId();
        c.addElement(new SequenceFlow(ctx.nextId(), originalTaskEvId, incCounterId));
        c.addElement(new ServiceTask(incCounterId, ExpressionType.SIMPLE, "${__retryUtils.inc(execution)}", null, null, true));

        // retry count GW
        String retryCountGwId = ctx.nextId();
        c.addElement(new SequenceFlow(ctx.nextId(), incCounterId, retryCountGwId));
        c.addElement(new ExclusiveGateway(retryCountGwId));

        // sleep
        String retryDelayId = ctx.nextId();
        c.addElement(new SequenceFlow(ctx.nextId(), retryCountGwId, retryDelayId, "${__retryUtils.isRetryCountExceeded(execution, " + retryParams.get("times") + ")}"));
        c.addElement(new ServiceTask(retryDelayId, ExpressionType.SIMPLE, "${__retryUtils.sleep(" + getRetryDelay(retryParams) + ")}", null, null, true));

        // retry task
        String retryTaskId = ctx.nextId();
        c.addElement(new SequenceFlow(ctx.nextId(), retryDelayId, retryTaskId));
        c.addElement(new ServiceTask(retryTaskId, ExpressionType.DELEGATE,
                "${" + s.getKey() + "}",
                getInVars(opts, retryParams), getVarMap(opts, "out"), true));
        c.addSourceMap(retryTaskId, toSourceMap(s, "Task: " + s.getKey() + " (retry)"));

        // cleanup
        String cleanupTaskId = ctx.nextId();
        c.addElement(new SequenceFlow(ctx.nextId(), retryTaskId, cleanupTaskId));
        c.addElement(new ServiceTask(cleanupTaskId, ExpressionType.SIMPLE, "${__retryUtils.cleanup(execution)}", null, null, true));
        c.addOutput(cleanupTaskId);

        String retryTaskEventId = ctx.nextId();
        c.addElement(new BoundaryEvent(retryTaskEventId, retryTaskId, null));
        c.addElement(new SequenceFlow(ctx.nextId(), retryTaskEventId, incCounterId));

        // throw last error
        String throwTaskId = ctx.nextId();
        String expr = "${__retryUtils.throwLastError(execution)}";
        c.addElement(new ServiceTask(throwTaskId, ExpressionType.DELEGATE, expr, null, null, true));
        c.addElement(new SequenceFlow(ctx.nextId(), retryCountGwId, throwTaskId));

        String endId = ctx.nextId();
        c.addElement(new EndEvent(endId));
        c.addElement(new SequenceFlow(ctx.nextId(),throwTaskId, endId));

        c.addSourceMaps(c.getSourceMap());
    }

    @SuppressWarnings("unchecked")
    private Set<VariableMapping> getInVars(Map<String, Object> opts, Map<String, Object> retryParams) {
        Map<String, Object> retryInVars = Optional.ofNullable((Map<String, Object>) retryParams.get("in")).orElse(Collections.emptyMap());
        Map<String, Object> originalInVars = toMap((Seq<KV<String, Object>>) opts.get("in"));
        return toVarMapping(ConfigurationUtils.deepMerge(originalInVars, retryInVars));
    }

    private static long getRetryDelay(Map<String, Object> params) throws YamlConverterException {
        Object v = params.get("delay");
        if (v == null) {
            return DEFAULT_DELAY;
        }

        if (!(v instanceof Integer)) {
            throw new YamlConverterException("Invalid 'delay' value. Expected an integer, got: " + v);
        }

        return TimeUnit.SECONDS.toMillis((int) v);
    }

    private static Set<VariableMapping> toVarMapping(Map<String, Object> params) {
        Set<VariableMapping> result = new HashSet<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String target = e.getKey();

            String sourceExpr = null;
            Object sourceValue = null;

            Object v = StepConverter.deepConvert(e.getValue());
            if (StepConverter.isExpression(v)) {
                sourceExpr = v.toString();
            } else {
                sourceValue = v;
            }

            result.add(new VariableMapping(null, sourceExpr, sourceValue, target, true));
        }

        return result;
    }

    private static Map<String, Object> toMap(Seq<KV<String, Object>> values) {
        if (values == null) {
            return Collections.emptyMap();
        }

        return values.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, YamlTaskStepConverter::toValue));
    }

    private static Object toValue(KV<String, Object> kv) {
        Object v = kv.getValue();
        if (v == null && kv.getKey() != null) {
            return false;
        }
        return StepConverter.deepConvert(v);
    }

    @Named("__retryUtils")
    public static class RetryUtilsTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(RetryUtilsTask.class);

        public void sleep(long t) {
            try {
                log.info("retry delay {} sec", t/1000);
                Thread.sleep(t);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public void throwLastError(ExecutionContext ctx) throws Throwable {
            Object lastError = ctx.getVariable(Constants.Context.LAST_ERROR_KEY);
            if (lastError instanceof BpmnError) {
                BpmnError e = (BpmnError) lastError;
                if (e.getCause() != null) {
                    throw e.getCause();
                } else {
                    throw e;
                }
            }

            throw new RuntimeException("retry count exceeded");
        }

        public void inc(ExecutionContext ctx) {
            int currentValue = getLastVariable(ctx, InternalConstants.Context.RETRY_COUNTER, 0);
            currentValue++;
            setLastVariable(ctx, InternalConstants.Context.RETRY_COUNTER, currentValue);
            ctx.setVariable(InternalConstants.Context.CURRENT_RETRY_COUNTER, currentValue);
        }

        public boolean isRetryCountExceeded(ExecutionContext ctx, int maxRetryCount) {
            return getLastVariable(ctx, InternalConstants.Context.RETRY_COUNTER, 0) <= maxRetryCount;
        }

        public void cleanup(ExecutionContext ctx) {
            clearLastVariable(ctx, InternalConstants.Context.RETRY_COUNTER);
            ctx.removeVariable(InternalConstants.Context.CURRENT_RETRY_COUNTER);
        }

        @SuppressWarnings("unchecked")
        private static <E> List<E> getList(ExecutionContext ctx, String name) {
            Object v = ctx.getVariable(name);
            if (v == null) {
                return new ArrayList<>();
            }

            if (v instanceof List) {
                return (List<E>) v;
            }

            throw new IllegalArgumentException("expected list with name '" + name + "', but got: " + v);
        }

        private static <E> E getLastVariable(ExecutionContext ctx, String name, E defaultValue) {
            List<E> v = getList(ctx, name);
            if (v.isEmpty()) {
                return defaultValue;
            }
            return v.get(v.size() - 1);
        }

        private static void setLastVariable(ExecutionContext ctx, String name, Object value) {
            List<Object> v = getList(ctx, name);
            if (v.isEmpty()) {
                v.add(value);
            } else {
                v.set(v.size() - 1, value);
            }
            ctx.setVariable(name, v);
        }

        private static void clearLastVariable(ExecutionContext ctx, String name) {
            List<Object> v = getList(ctx, name);
            v.remove(v.size() - 1);
            if (v.isEmpty()) {
                ctx.removeVariable(name);
            } else {
                ctx.setVariable(name, v);
            }
        }
    }
}
