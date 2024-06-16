package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.Loop;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.compiler.CompilerContext;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class LoopWrapper implements Command {

    public static LoopWrapper of(CompilerContext ctx, Command cmd, Loop withItems, Collection<String> outVariables, Map<String, Serializable> outExpressions, Step step) {
        Collection<String> out = Collections.emptyList();
        if (!outExpressions.isEmpty()) {
            // serializable
            out = new HashSet<>(outExpressions.keySet());
        } else if (!outVariables.isEmpty()) {
            out = outVariables;
        }

        Loop.Mode mode = withItems.mode();
        switch (mode) {
            case SERIAL:
                return new SerialWithItems(cmd, withItems, out, step);
            case PARALLEL:
                return new ParallelWithItems(ctx, cmd, withItems, out, step);
            default:
                throw new IllegalArgumentException("Unknown withItems mode: " + mode);
        }
    }

    private static final long serialVersionUID = 1L;

    // TODO move into the actual Constants
    public static final String CURRENT_ITEMS = "items";
    public static final String CURRENT_INDEX = "itemIndex";
    public static final String CURRENT_ITEM = "item";

    protected final Command cmd;
    protected final Serializable items;
    protected final Collection<String> outVariables;
    private final Step step;

    protected LoopWrapper(Command cmd, Serializable items, Collection<String> outVariables, Step step) {
        this.cmd = cmd;
        this.items = items;
        this.outVariables = outVariables;
        this.step = step;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        try {
            execute(runtime, state, threadId);
        } catch (Exception e) {
            StepCommand.logException(step, state, threadId, e);
            throw new LoggedException(e);
        }
    }

    private void execute(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        Serializable value = items;
        if (value == null) {
            // value is null, not going to run the wrapped command at all
            return;
        }

        // create the context explicitly
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        Context ctx = contextFactory.create(runtime, state, threadId, getCurrentStep());

        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        value = ee.eval(ecf.global(ctx), value, Serializable.class);

        // prepare items
        // store items in an ArrayList because it is Serializable
        ArrayList<Serializable> items = LoopItemSanitizer.sanitize(value);
        if (items == null || items.isEmpty()) {
            return;
        }

        eval(runtime, state, threadId, ctx, items);
    }

    protected abstract void eval(Runtime runtime, State state, ThreadId threadId, Context ctx, ArrayList<Serializable> items);

    private Step getCurrentStep() {
        if (cmd instanceof StepCommand) {
            return ((StepCommand<?>) cmd).getStep();
        }
        return null;
    }

    static class ParallelWithItems extends LoopWrapper {

        private static final long serialVersionUID = 1L;

        private final Parallelism parallelism;

        protected ParallelWithItems(CompilerContext ctx, Command cmd, Loop loop, Collection<String> outVariables, Step step) {
            super(cmd, loop.items(), outVariables, step);

            this.parallelism = processParallelism(ctx, loop);
        }

        private ParallelWithItems(Command cmd, Serializable items, Collection<String> outVariables, Parallelism parallelism, Step step) {
            super(cmd, items, outVariables, step);
            this.parallelism = parallelism;
        }

        private static Parallelism processParallelism(CompilerContext ctx, Loop loop) {
            int defaultParallelism = ctx.processDefinition().configuration().parallelLoopParallelism();

            Object parallelism = loop.options().get("parallelism");
            if (parallelism == null) {
                return new Parallelism(null, defaultParallelism);
            } else if (parallelism instanceof String) {
                return new Parallelism((String) parallelism, defaultParallelism);
            } else if (parallelism instanceof Number) {
                return new Parallelism(null, ((Number) parallelism).intValue());
            }

            throw new RuntimeException("Unknown loop parallelism value type: " + parallelism.getClass());
        }

        @Override
        protected void eval(Runtime runtime, State state, ThreadId threadId, Context ctx, ArrayList<Serializable> items) {
            // target frame for out variables
            Frame targetFrame = VMUtils.assertNearestRoot(state, threadId);

            Map<String, List<Serializable>> outVarsAccumulator = new ConcurrentHashMap<>();
            state.pushFrame(threadId, Frame.builder()
                    .commands(new SetVariablesCommand(outVarsAccumulator, targetFrame))
                    .nonRoot()
                    .build());

            int batchSize = toBatchSize(runtime, ctx, parallelism);

            List<ArrayList<Serializable>> batches = batches(items, batchSize);
            int itemIndexStart = 0;
            for (ArrayList<Serializable> batch : batches) {
                evalBatch(itemIndexStart, state, threadId, batch, outVarsAccumulator);
                itemIndexStart += batch.size();
            }
        }

        private void evalBatch(int itemIndexStart, State state, ThreadId threadId, ArrayList<Serializable> items, Map<String, List<Serializable>> outVarsAccumulator) {
            Frame frame = state.peekFrame(threadId);

            List<Map.Entry<ThreadId, Serializable>> forks = items.stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(state.nextThreadId(), e))
                    .collect(Collectors.toList());

            for (int i = 0; i < forks.size(); i++) {
                Map.Entry<ThreadId, Serializable> f = forks.get(i);
                Frame cmdFrame = Frame.builder()
                        .nonRoot()
                        .build();

                cmdFrame.setLocal(CURRENT_ITEMS, items);
                cmdFrame.setLocal(CURRENT_INDEX, itemIndexStart + i);
                cmdFrame.setLocal(CURRENT_ITEM, f.getValue());

                // fork will create rootFrame for forked commands
                Command itemCmd = new ForkCommand(f.getKey(),
                        new CollectVariablesCommand(outVariables, null, outVarsAccumulator),
                        cmd);
                cmdFrame.push(itemCmd);

                state.pushFrame(threadId, cmdFrame);
            }

            frame.push(new JoinCommand(forks.stream().map(Map.Entry::getKey).collect(Collectors.toSet())));
        }

        private static List<ArrayList<Serializable>> batches(ArrayList<Serializable> items, int batchSize) {
            List<ArrayList<Serializable>> result = new ArrayList<>();
            for (int i = 0; i < items.size(); i += batchSize) {
                result.add(new ArrayList<>(items.subList(i, Math.min(items.size(), i + batchSize))));
            }
            return result;
        }

        private static int toBatchSize(Runtime runtime, Context ctx, Parallelism parallelism) {
            if (parallelism.getExpression() == null) {
                return parallelism.getValue();
            }

            EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
            EvalContext evalContext = ecf.global(ctx);

            ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
            Number result = ee.eval(evalContext, parallelism.getExpression(), Number.class);
            if (result == null) {
                return parallelism.getValue();
            }
            return result.intValue();
        }
    }

    /**
     * Wraps a command into a loop specified by {@code withItems} option.
     * Creates a new call frame and keeps the item list, the current item
     * and the index as frame-local variables.
     */
    static class SerialWithItems extends LoopWrapper {

        private static final long serialVersionUID = 1L;

        protected SerialWithItems(Command cmd, Loop loop, Collection<String> outVariables, Step step) {
            super(cmd, loop.items(), outVariables, step);
        }

        @Override
        protected void eval(Runtime runtime, State state, ThreadId threadId, Context ctx, ArrayList<Serializable> items) {
            Frame loop = Frame.builder()
                    .nonRoot()
                    .build();

            loop.setLocal(CURRENT_ITEMS, items);
            loop.setLocal(CURRENT_INDEX, 0);
            loop.setLocal(CURRENT_ITEM, items.get(0));

            Map<String, List<Serializable>> variablesAccumulator = new ConcurrentHashMap<>();

            Frame targetFrame = VMUtils.assertNearestRoot(state, threadId);
            loop.push(new SetVariablesCommand(variablesAccumulator, targetFrame));

            loop.push(new WithItemsNext(outVariables, variablesAccumulator, cmd)); // next iteration

            Frame cmdFrame = Frame.builder()
                    .commands(cmd)
                    .root()
                    .build();

            loop.push(new CollectVariablesCommand(outVariables, cmdFrame, variablesAccumulator));

            state.pushFrame(threadId, loop);
            state.pushFrame(threadId, cmdFrame);
        }
    }

    static class WithItemsNext implements Command {

        private static final long serialVersionUID = 1L;

        private final Collection<String> outVariables;
        private final Map<String, List<Serializable>> variablesAccumulator;
        private final Command cmd;

        public WithItemsNext(Collection<String> outVariables, Map<String, List<Serializable>> variablesAccumulator, Command cmd) {
            this.outVariables = outVariables;
            this.variablesAccumulator = variablesAccumulator;
            this.cmd = cmd;
        }

        @Override
        public void eval(Runtime runtime, State state, ThreadId threadId) {
            Frame loop = state.peekFrame(threadId);
            loop.pop();

            List<Serializable> items = VMUtils.assertLocal(state, threadId, CURRENT_ITEMS);

            int index = VMUtils.assertLocal(state, threadId, CURRENT_INDEX);
            if (index + 1 >= items.size()) {
                // end of the line, do nothing
                return;
            }

            int newIndex = index + 1;
            loop.setLocal(CURRENT_INDEX, newIndex);
            loop.setLocal(CURRENT_ITEM, items.get(newIndex));

            loop.push(new WithItemsNext(outVariables, variablesAccumulator, cmd)); // next iteration

            // frame wrapped command
            Frame cmdFrame = Frame.builder()
                    .commands(cmd)
                    .root()
                    .build();

            loop.push(new CollectVariablesCommand(outVariables, cmdFrame, variablesAccumulator));

            state.pushFrame(threadId, cmdFrame);
        }
    }

    static class SetVariablesCommand implements Command {

        private static final long serialVersionUID = 1L;

        private final Map<String, List<Serializable>> variables;
        private final Frame targetFrame;

        private SetVariablesCommand(Map<String, List<Serializable>> variables, Frame targetFrame) {
            this.variables = variables;
            this.targetFrame = targetFrame;
        }

        @Override
        public void eval(Runtime runtime, State state, ThreadId threadId) throws Exception {
            Frame frame = state.peekFrame(threadId);
            frame.pop();

            if (variables.isEmpty()) {
                return;
            }

            for (Map.Entry<String, List<Serializable>> e : variables.entrySet()) {
                VMUtils.putLocal(targetFrame, e.getKey(), e.getValue());
            }
        }
    }

    /**
     * Collect values of the specified variables from the source frame or nearest root frame into
     * internal accumulator.
     */
    // TODO: BRIG: step command, with handle error?
    static class CollectVariablesCommand implements Command {

        private static final long serialVersionUID = 1L;

        private final Collection<String> variables;
        private final Map<String, List<Serializable>> variablesAccumulator;

        private final Frame sourceFrame;

        public CollectVariablesCommand(Collection<String> variables, Frame sourceFrame, Map<String, List<Serializable>> variablesAccumulator) {
            this.variables = variables;
            this.sourceFrame = sourceFrame;
            this.variablesAccumulator = variablesAccumulator;
        }

        @Override
        public void eval(Runtime runtime, State state, ThreadId threadId) throws Exception {
            Frame frame = state.peekFrame(threadId);
            frame.pop();

            if (variables.isEmpty()) {
                return;
            }

            Frame effectiveSourceFrame = sourceFrame != null ? sourceFrame : VMUtils.assertNearestRoot(state, threadId);

            for (String var : variables) {
                Serializable result = effectiveSourceFrame.hasLocal(var) ? effectiveSourceFrame.getLocal(var) : null;

                variablesAccumulator.compute(var, (k, v) -> {
                    List<Serializable> values = v;
                    if (values == null) {
                        values = new ArrayList<>();
                    }
                    values.add(result);
                    return values;
                });
            }
        }
    }

    private static class Parallelism implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String expression;
        private final int value;

        public Parallelism(String expression, int value) {
            this.expression = expression;
            this.value = value;
        }

        public String getExpression() {
            return expression;
        }

        public int getValue() {
            return value;
        }
    }
}