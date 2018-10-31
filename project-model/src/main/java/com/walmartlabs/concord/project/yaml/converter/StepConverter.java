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
import com.walmartlabs.concord.project.yaml.KV;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlStep;
import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.model.*;
import io.takari.bpm.model.SourceMap.Significance;
import io.takari.parc.Seq;

import javax.inject.Named;
import java.io.Serializable;
import java.util.*;

public interface StepConverter<T extends YamlStep> {

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

    @SuppressWarnings("unchecked")
    default Chunk applyWithItems(ConverterContext ctx, Chunk c, Map<String, Object> opts) throws YamlConverterException {
        Object withItems = getWithItems(opts);
        if (withItems == null) {
            return c;
        }

        Chunk result = new Chunk();

        VariableMapping taskVars = new VariableMapping(null, null, withItems, "items", true);

        String startId        = ctx.nextId();
        String initId         = ctx.nextId();
        String nextItemTaskId = ctx.nextId();
        String hasNextGwId    = ctx.nextId();
        String cleanupTaskId  = ctx.nextId();

        result.addElement(new ServiceTask(initId, ExpressionType.SIMPLE, "${__withItemsUtils.init(execution)}", Collections.singleton(taskVars), null, true));
        result.addElement(new ServiceTask(nextItemTaskId, ExpressionType.SIMPLE, "${__withItemsUtils.nextItem(execution)}", Collections.singleton(taskVars), null, true));
        result.addElement(new ExclusiveGateway(hasNextGwId));
        result.addElement(new ServiceTask(cleanupTaskId, ExpressionType.SIMPLE, "${__withItemsUtils.cleanup(execution)}", null, null, true));

        /*  ::bpm flow::            --------theTask ----Y
                                    v                   |
         startId -----> init -> hasNextTaskId -----> hasNextGwId --N--> cleanupTaskId
         */
        result.addElement(new SequenceFlow(ctx.nextId(), startId, initId));
        result.addElement(new SequenceFlow(ctx.nextId(), initId, nextItemTaskId));
        result.addElement(new SequenceFlow(ctx.nextId(), nextItemTaskId, hasNextGwId));
        result.addElement(new SequenceFlow(ctx.nextId(), hasNextGwId, c.firstElement().getId(), "${__withItemsUtils.hasNext(execution)}"));
        result.addElement(new SequenceFlow(ctx.nextId(), hasNextGwId, cleanupTaskId));
        c.getOutputs().forEach(o -> {
            result.addElement(new SequenceFlow(ctx.nextId(), o, nextItemTaskId));
        });

        result.addOutput(cleanupTaskId);

        result.addElements(c.getElements());
        result.addSourceMaps(c.getSourceMap());

        return result;
    }

    @SuppressWarnings("unchecked")
    static Object getWithItems(Map<String, Object> options) {
        if (options == null) {
            return null;
        }

        Object withItems = options.get("withItems");
        if (withItems == null) {
            return null;
        }

        if (withItems instanceof String) {
            return withItems;
        }

        return ((Seq)withItems).toList();
    }

    default SourceMap toSourceMap(YamlStep step, String description) {
        JsonLocation l = step.getLocation();
        return new SourceMap(Significance.HIGH, String.valueOf(l.getSourceRef()), l.getLineNr(), l.getColumnNr(), description);
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

    @SuppressWarnings("unchecked")
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

            Map<Object, Object> dst = new HashMap<>(src.size());
            for (Map.Entry<Object, Object> e : src.entrySet()) {
                dst.put(e.getKey(), deepConvert(e.getValue()));
            }

            return dst;
        }

        return o;
    }

    @SuppressWarnings("unchecked")
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

        public void init(ExecutionContext ctx) {
            List<Integer> currentIndex = getList(ctx, CURRENT_INDEX);
            currentIndex.add(0);
            ctx.setVariable(CURRENT_INDEX, currentIndex);

            List<Boolean> hasNext = getList(ctx, HAS_NEXT);
            hasNext.add(false);
            ctx.setVariable(HAS_NEXT, hasNext);
        }

        /**
         * Checks for an item to process next. Will set variables within the execution context with the results.
         * <ul>
         * <li><code>__withItemsHasNext</code> - boolean - true if there's an item to process</li>
         * <li><code>item</code> - Object - item to be processed</li>
         * </ul>
         * @param ctx context containing execution variables
         */
        @SuppressWarnings("unchecked")
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

        public boolean hasNext(ExecutionContext ctx) {
            return getLastVariable(ctx, HAS_NEXT);
        }

        public void cleanup(ExecutionContext ctx) {
            clearLastVariable(ctx, CURRENT_INDEX);
            ctx.removeVariable(ITEM);
            clearLastVariable(ctx, HAS_NEXT);
        }

        @SuppressWarnings("unchecked")
        private static List<Object> assertItems(ExecutionContext ctx) {
            Object result = ctx.getVariable("items");
            if (result == null) {
                return new ArrayList<>(0);
            }

            if (result instanceof List) {
                return (List<Object>)result;
            }

            throw new IllegalArgumentException("'withItems' values should be a list, got: " + result.getClass());
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
}
