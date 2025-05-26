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

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.project.yaml.KV;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlStep;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.api.BpmnError;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.model.*;
import io.takari.bpm.model.SourceMap.Significance;
import io.takari.parc.Seq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public interface StepConverter<T extends YamlStep> {

    long DEFAULT_DELAY = 5;

    Chunk convert(ConverterContext ctx, T s) throws YamlConverterException;

    @SuppressWarnings("unchecked")
    default void applyErrorBlock(ConverterContext ctx, Chunk c, String attachedRef, Map<String, Object> opts) throws YamlConverterException {
        if (opts == null) {
            return;
        }

        Seq<YamlStep> errorSteps = (Seq<YamlStep>) opts.get("error");
        if (errorSteps == null) {
            return;
        }

        String evId = ctx.nextId();
        c.addElement(new BoundaryEvent(evId, attachedRef, null));

        Chunk err = ctx.convert(errorSteps);

        // connect the boundary event to the error block's steps
        String dst = err.firstElement().getId();
        c.addElement(new SequenceFlow(ctx.nextId(), evId, dst));
        c.addElements(err.getElements());

        c.addOutputs(err.getOutputs());

        // keep the source map of the error block's steps
        c.addSourceMaps(err.getSourceMap());
    }

    default Chunk applyWithItems(ConverterContext ctx, Chunk c, Map<String, Object> opts) throws YamlConverterException {
        Object withItems = getWithItems(opts);
        if (withItems == null) {
            return c;
        }

        Chunk result = new Chunk();

        VariableMapping taskVars = new VariableMapping(null, null, withItems, "items", true);
        Set<String> outVars = Optional.ofNullable(getVarMap(opts, "out")).map(vars -> vars.stream().map(VariableMapping::getTarget).collect(Collectors.toSet())).orElse(Collections.emptySet());
        VariableMapping outVarsMapping = new VariableMapping(null, null, outVars, "__0", true);

        String startId = ctx.nextId();
        String initId = ctx.nextId();
        String nextItemTaskId = ctx.nextId();
        String processOutVarsTask = ctx.nextId();
        String hasNextGwId = ctx.nextId();
        String cleanupTaskId = ctx.nextId();

        result.addElement(new ServiceTask(initId, ExpressionType.SIMPLE, "${__withItemsUtils.init(execution)}", Collections.singleton(taskVars), null, true));
        result.addElement(new ServiceTask(nextItemTaskId, ExpressionType.SIMPLE, "${__withItemsUtils.nextItem(execution)}", Collections.singleton(taskVars), null, true));
        result.addElement(new ServiceTask(processOutVarsTask, ExpressionType.SIMPLE, "${__withItemsUtils.processOutVars(execution, __0)}", Collections.singleton(outVarsMapping), null, true));
        result.addElement(new ExclusiveGateway(hasNextGwId));
        result.addElement(new ServiceTask(cleanupTaskId, ExpressionType.SIMPLE, "${__withItemsUtils.cleanup(execution, __0)}", Collections.singleton(outVarsMapping), null, true));

        /*  ::bpm flow::            ---processOutVarsTask <---- theTask ----Y
                                    v                                       |
         startId -----> init -> nextItemTaskId --------------------> hasNextGwId --N--> cleanupTaskId
         */
        result.addElement(new SequenceFlow(ctx.nextId(), startId, initId));
        result.addElement(new SequenceFlow(ctx.nextId(), initId, nextItemTaskId));
        result.addElement(new SequenceFlow(ctx.nextId(), nextItemTaskId, hasNextGwId));
        result.addElement(new SequenceFlow(ctx.nextId(), processOutVarsTask, nextItemTaskId));
        result.addElement(new SequenceFlow(ctx.nextId(), hasNextGwId, c.firstElement().getId(), "${__withItemsUtils.hasNext(execution)}"));
        result.addElement(new SequenceFlow(ctx.nextId(), hasNextGwId, cleanupTaskId));
        c.getOutputs().forEach(o -> {
            result.addElement(new SequenceFlow(ctx.nextId(), o, processOutVarsTask));
        });

        result.addOutput(cleanupTaskId);

        result.addElements(c.getElements());
        result.addSourceMaps(c.getSourceMap());

        return result;
    }

    @SuppressWarnings("unchecked")
    default void applyRetryBlock(ConverterContext ctx, Chunk c, String attachedRef, JsonLocation loc, Map<String, Object> opts,
                                 BiFunction<String, Map<String, Object>, String> f) throws YamlConverterException {

        if (opts == null) {
            return;
        }

        Map<String, Object> retryParams = toMap((Seq<KV<String, Object>>) opts.get("retry"));
        if (retryParams.isEmpty()) {
            return;
        }

        c.removeOutput(attachedRef);

        VariableMapping retryCountVar = new VariableMapping(null, null, retryParams.get("times"), "__maxRetryCount", true);
        VariableMapping delayVar = new VariableMapping(null, null, getRetryDelay(retryParams, loc), "__retryDelay", true);

        // retry init
        String initId = ctx.nextId();
        c.addFirstElement(new ServiceTask(initId, ExpressionType.SIMPLE, "${__retryUtils.init(execution)}", null, null, true));
        c.addElement(new SequenceFlow(ctx.nextId(), initId, attachedRef));

        // boundary event
        String originalEvId = ctx.nextId();
        c.addElement(new BoundaryEvent(originalEvId, attachedRef, null));

        // inc retry count
        String incCounterId = ctx.nextId();
        c.addElement(new SequenceFlow(ctx.nextId(), originalEvId, incCounterId));
        c.addElement(new ServiceTask(incCounterId, ExpressionType.SIMPLE, "${__retryUtils.inc(execution)}", Collections.singleton(retryCountVar), null, true));

        // retry count GW
        String retryCountGwId = ctx.nextId();
        c.addElement(new SequenceFlow(ctx.nextId(), incCounterId, retryCountGwId));
        c.addElement(new ExclusiveGateway(retryCountGwId));

        // sleep
        String retryDelayId = ctx.nextId();
        c.addElement(new SequenceFlow(ctx.nextId(), retryCountGwId, retryDelayId, "${__retryUtils.isRetryCountExceeded(execution)}"));
        c.addElement(new ServiceTask(retryDelayId, ExpressionType.SIMPLE, "${__retryUtils.sleep(execution)}", Collections.singleton(delayVar), null, true));

        // retry step
        String retryId = f.apply(retryDelayId, retryParams);

        // cleanup
        String cleanupTaskId = ctx.nextId();
        c.addElement(new SequenceFlow(ctx.nextId(), retryId, cleanupTaskId));
        c.addElement(new ServiceTask(cleanupTaskId, ExpressionType.SIMPLE, "${__retryUtils.cleanup(execution)}", null, null, true));
        c.addOutput(cleanupTaskId);

        String retryEventId = ctx.nextId();
        c.addElement(new BoundaryEvent(retryEventId, retryId, null));
        c.addElement(new SequenceFlow(ctx.nextId(), retryEventId, incCounterId));

        // throw last error
        String throwCallId = ctx.nextId();
        String expr = "${__retryUtils.throwLastError(execution)}";
        c.addElement(new ServiceTask(throwCallId, ExpressionType.DELEGATE, expr, null, null, true));
        c.addElement(new SequenceFlow(ctx.nextId(), retryCountGwId, throwCallId));

        String endId = ctx.nextId();
        c.addElement(new EndEvent(endId));
        c.addElement(new SequenceFlow(ctx.nextId(), throwCallId, endId));

        // cleanup after success execution
        c.addElement(new SequenceFlow(ctx.nextId(), attachedRef, cleanupTaskId));

        c.addSourceMaps(c.getSourceMap());
    }

    static Object getWithItems(Map<String, Object> options) {
        if (options == null) {
            return null;
        }

        Object withItems = options.get("withItems");
        if (withItems == null) {
            return null;
        }

        return deepConvert(withItems);
    }

    default SourceMap toSourceMap(YamlStep step, String description) {
        JsonLocation l = step.getLocation();
        return new SourceMap(Significance.HIGH, String.valueOf(l.getSourceRef()), l.getLineNr(), l.getColumnNr(), description);
    }

    default Map<String, Object> toMap(Seq<KV<String, Object>> values) {
        if (values == null) {
            return Collections.emptyMap();
        }

        return values.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, this::toValue));
    }

    default Object toValue(KV<String, Object> kv) {
        Object v = kv.getValue();
        if (v == null && kv.getKey() != null) {
            return false;
        }
        return StepConverter.deepConvert(v);
    }

    default Set<VariableMapping> toVarMapping(Map<String, Object> params) {
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

    @SuppressWarnings("unchecked")
    default Set<VariableMapping> getVarMap(Map<String, Object> options, String key) {
        if (options == null) {
            return null;
        }

        Seq<KV<String, Object>> s = (Seq<KV<String, Object>>) options.get(key);
        if (s == null) {
            return null;
        }

        Set<VariableMapping> result = new HashSet<>();
        for (KV<String, Object> kv : s.toList()) {
            String target = kv.getKey();

            String sourceExpr = null;
            Object sourceValue = null;

            Object v = deepConvert(kv.getValue());
            if (isExpression(v)) {
                sourceExpr = v.toString();
            } else {
                sourceValue = v;
            }

            result.add(new VariableMapping(null, sourceExpr, sourceValue, target, true));
        }

        if (key.equals("in") && getWithItems(options) != null) {
            result = appendWithItemsVar(result);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    default Set<VariableMapping> getInVars(Map<String, Object> opts, Map<String, Object> retryParams) {
        Map<String, Object> retryInVars = Optional.ofNullable((Map<String, Object>) retryParams.get("in")).orElse(Collections.emptyMap());
        Map<String, Object> originalInVars = toMap((Seq<KV<String, Object>>) opts.get("in"));
        return toVarMapping(ConfigurationUtils.deepMerge(originalInVars, retryInVars));
    }

    default Object getRetryDelay(Map<String, Object> params, JsonLocation loc) throws YamlConverterException {
        Object v = params.get("delay");
        if (v == null) {
            return DEFAULT_DELAY;
        }

        if (v instanceof Integer) {
            return v;
        }

        if (isExpression(v)) {
            return v;
        }

        throw new YamlConverterException("Invalid 'delay' value. Expected integer or expression, got: " + v + " @ " + loc);
    }

    static Set<VariableMapping> appendWithItemsVar(Set<VariableMapping> inVars) {
        Set<VariableMapping> vars = new HashSet<>();
        if (inVars != null) {
            vars.addAll(inVars);
        }
        vars.add(new VariableMapping(null, "${item}", null, "item", true));
        return vars;
    }

    static boolean isExpression(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof String)) {
            return false;
        }

        String s = (String) o;
        int i = s.indexOf("${");
        return i >= 0 && s.indexOf("}", i) > i;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object deepConvert(Object o) {
        if (o instanceof Seq) {
            List<Object> src = ((Seq) o).toList();

            List<Object> dst = new ArrayList<>(src.size());
            for (Object s : src) {
                dst.add(deepConvert(s));
            }

            return dst;
        } else if (o instanceof Map) {
            Map<Object, Object> src = (Map<Object, Object>) o;

            Map<Object, Object> dst = new LinkedHashMap<>(src.size());
            for (Map.Entry<Object, Object> e : src.entrySet()) {
                dst.put(e.getKey(), deepConvert(e.getValue()));
            }

            return dst;
        }

        return o;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    default ELCall createELCall(String task, Object args) {
        StringBuilder b = new StringBuilder("${");
        b.append(task).append(".call(");

        Set<VariableMapping> maps = new HashSet<>();

        if (args != null) {
            args = deepConvert(args);

            if (args instanceof List) {
                int idx = 0;
                for (Iterator<Object> i = ((List) args).iterator(); i.hasNext(); ) {
                    String k = "__" + idx++;
                    Object v = i.next();
                    maps.add(new VariableMapping(null, null, v, k, true));

                    b.append(k);
                    if (i.hasNext()) {
                        b.append(", ");
                    }
                }
            } else {
                String k = "__0";
                if (isExpression(args)) {
                    String s = args.toString().trim();
                    maps.add(new VariableMapping(null, s, null, k));
                } else {
                    maps.add(new VariableMapping(null, null, args, k, true));
                }
                b.append(k);
            }
        }

        b.append(")}");

        return new ELCall(b.toString(), maps.isEmpty() ? null : maps);
    }

    class ELCall implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String expression;
        private final Set<VariableMapping> args;

        private ELCall(String expression, Set<VariableMapping> args) {
            this.expression = expression;
            this.args = args;
        }

        public String getExpression() {
            return expression;
        }

        public Set<VariableMapping> getArgs() {
            return args;
        }
    }

    @Named("__withItemsUtils")
    class WithItemsUtilsTask implements Task {
        private static final String CURRENT_INDEX = "__currentWithItemIndex";
        private static final String HAS_NEXT = "__withItemsHasNext";
        private static final String ITEM = "item";
        private static final String ITEM_HISTORY = "__withItemsItem";

        public void init(ExecutionContext ctx) {
            List<Integer> currentIndex = getList(ctx, CURRENT_INDEX);
            currentIndex.add(0);
            ctx.setVariable(CURRENT_INDEX, currentIndex);

            List<Boolean> hasNext = getList(ctx, HAS_NEXT);
            hasNext.add(false);
            ctx.setVariable(HAS_NEXT, hasNext);

            ctx.setVariable("__withItems_keysBeforeTask", new HashSet<>(ctx.toMap().keySet()));

            // store current item variable
            List<Object> item = getList(ctx, ITEM_HISTORY);
            item.add(ctx.getVariable(ITEM));
            ctx.setVariable(ITEM_HISTORY, item);
        }

        /**
         * Checks for an item to process next. Will set variables within the execution context with the results.
         * <ul>
         * <li><code>__withItemsHasNext</code> - boolean - true if there's an item to process</li>
         * <li><code>item</code> - Object - item to be processed</li>
         * </ul>
         *
         * @param ctx context containing execution variables
         */
        @SuppressWarnings("unused")
        public void nextItem(ExecutionContext ctx) {
            int currentItemIndex = getLastVariable(ctx, CURRENT_INDEX);

            List<Object> items = assertItems(ctx);

            if (currentItemIndex < items.size()) {
                ctx.setVariable(ITEM, items.get(currentItemIndex));
            }

            setLastVariable(ctx, HAS_NEXT, currentItemIndex < items.size());
            currentItemIndex++;
            setLastVariable(ctx, CURRENT_INDEX, currentItemIndex);
        }

        @SuppressWarnings("unused")
        public boolean hasNext(ExecutionContext ctx) {
            return getLastVariable(ctx, HAS_NEXT);
        }

        @SuppressWarnings("unused")
        public void processOutVars(ExecutionContext ctx, Set<String> taskOutVars) {
            // restore current item variable
            ctx.setVariable(ITEM, getLastVariable(ctx, ITEM_HISTORY));

            Set<String> taskVariables = taskOutVars;
            if (taskVariables.isEmpty()) {
                taskVariables = collectOutVars(ctx);
            }

            taskVariables.forEach(v -> {
                String tmpVarName = "__withItems_" + v;
                List<Object> results = getList(ctx, tmpVarName);
                results.add(ctx.getVariable(v));
                ctx.setVariable(tmpVarName, results);
            });
        }

        @SuppressWarnings("unused")
        public void cleanup(ExecutionContext ctx, Set<String> taskOutVars) {
            clearLastVariable(ctx, CURRENT_INDEX);
            ctx.removeVariable(ITEM);
            clearLastVariable(ctx, HAS_NEXT);

            Set<String> taskVariables = taskOutVars;
            if (taskVariables.isEmpty()) {
                taskVariables = collectOutVars(ctx);
            }
            ctx.removeVariable("__withItems_keysBeforeTask");

            clearLastVariable(ctx, ITEM_HISTORY);

            taskVariables.forEach(v -> {
                String tmpVarName = "__withItems_" + v;
                Object var = ctx.getVariable(tmpVarName);
                ctx.removeVariable(tmpVarName);
                ctx.setVariable(v, var);
            });
        }

        @SuppressWarnings("unchecked")
        private static Set<String> collectOutVars(ExecutionContext ctx) {
            Set<String> before = (Set<String>) ctx.getVariable("__withItems_keysBeforeTask");
            return ctx.toMap().keySet().stream()
                    .filter(v -> !v.startsWith("__"))
                    .filter(v -> !v.equals(ITEM))
                    .filter(v -> !before.contains(v))
                    .collect(Collectors.toSet());
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static List<Object> assertItems(ExecutionContext ctx) {
            Object result = ctx.getVariable("items");
            if (result == null) {
                return new ArrayList<>(0);
            }

            if (result instanceof List) {
                return (List<Object>) result;
            }

            if (result.getClass().isArray()) {
                return Arrays.asList((Object[]) result);
            }

            if (result instanceof Map) {
                return new ArrayList<>(((Map) result).entrySet());
            }

            throw new IllegalArgumentException("'withItems' values should be a list or an array, got: " + result.getClass());
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

            if (v.getClass().isArray()) {
                return Arrays.asList((E[]) v);
            }

            throw new IllegalArgumentException("expected list with name '" + name + "', but got: " + v);
        }

        private static <E> E getLastVariable(ExecutionContext ctx, String name) {
            List<E> v = getList(ctx, name);
            return v.get(v.size() - 1);
        }

        private static void setLastVariable(ExecutionContext ctx, String name, Object value) {
            List<Object> v = getList(ctx, name);
            v.set(v.size() - 1, value);
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

    @Named("__retryUtils")
    class RetryUtilsTask implements Task {

        private static final Logger log = LoggerFactory.getLogger(RetryUtilsTask.class);

        public void sleep(ExecutionContext ctx) {
            int t = assertInt(ctx, "__retryDelay");

            try {
                log.info("retry delay {} sec", t);
                Thread.sleep(TimeUnit.SECONDS.toMillis(t));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private int assertInt(ExecutionContext ctx, String name) {
            Object v = ctx.getVariable(name);
            if (v == null) {
                throw new IllegalArgumentException("Variable '" + name + "' not found");
            }
            if (v instanceof Number) {
                return ((Number) v).intValue();
            }
            if (v instanceof String) {
                return Integer.parseInt((String)v);
            }
            throw new IllegalArgumentException("Invalid variable '" + name + "' type, expected: integer, got: " + v.getClass());
        }

        @SuppressWarnings("unused")
        public void throwLastError(ExecutionContext ctx) throws Throwable {
            clearLastVariable(ctx, InternalConstants.Context.RETRY_COUNTER);

            Object lastError = ctx.getVariable(Constants.Context.LAST_ERROR_KEY);
            if (lastError instanceof BpmnError) {
                BpmnError e = (BpmnError) lastError;
                if (e.getCause() != null) {
                    throw e.getCause();
                } else {
                    throw e;
                }
            }

            throw new RuntimeException("Retry count exceeded");
        }

        public void init(ExecutionContext ctx) {
            List<Integer> retryCounter = getList(ctx, InternalConstants.Context.RETRY_COUNTER);
            retryCounter.add(0);
            ctx.setVariable(InternalConstants.Context.RETRY_COUNTER, retryCounter);

            ctx.setVariable(InternalConstants.Context.CURRENT_RETRY_COUNTER, 0);
        }

        public void inc(ExecutionContext ctx) {
            int currentValue = getLastVariable(ctx, InternalConstants.Context.RETRY_COUNTER, 0);
            currentValue++;
            setLastVariable(ctx, InternalConstants.Context.RETRY_COUNTER, currentValue);
            ctx.setVariable(InternalConstants.Context.CURRENT_RETRY_COUNTER, currentValue);
            int maxRetryCount = assertInt(ctx, "__maxRetryCount");
            ctx.setVariable("__retryCountExceeded", currentValue <= maxRetryCount);
        }

        @SuppressWarnings("unused")
        public boolean isRetryCountExceeded(ExecutionContext ctx) {
            return (boolean) ctx.getVariable("__retryCountExceeded");
        }

        @SuppressWarnings("unused")
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

            throw new IllegalArgumentException("Expected a list with name '" + name + "', got: " + v);
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
