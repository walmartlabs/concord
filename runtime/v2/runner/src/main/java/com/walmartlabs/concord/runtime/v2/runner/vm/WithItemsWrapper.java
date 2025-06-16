package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.model.WithItems;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @deprecated use {@link com.walmartlabs.concord.runtime.v2.runner.vm.LoopWrapper}
 */
@Deprecated
public abstract class WithItemsWrapper implements Command {

    public static WithItemsWrapper of(Command cmd, WithItems withItems,
                                      Collection<String> outVariables,
                                      Map<String, Serializable> outMapping,
                                      Step step) {
        Collection<String> out = Collections.emptyList();
        if (!outMapping.isEmpty()) {
            out = new HashSet<>(outMapping.keySet());
        } else if (!outVariables.isEmpty()) {
            out = outVariables;
        }

        WithItems.Mode mode = withItems.mode();
        switch (mode) {
            case SERIAL:
                return new SerialWithItems(cmd, withItems, out);
            case PARALLEL:
                return new ParallelWithItems(cmd, withItems, out, step);
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
    protected final WithItems withItems;
    protected final Collection<String> outVariables;

    protected WithItemsWrapper(Command cmd, WithItems withItems, Collection<String> outVariables) {
        this.cmd = cmd;
        this.withItems = withItems;
        this.outVariables = outVariables;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        Serializable value = withItems.value();
        if (value == null) {
            // value is null, not going to run the wrapped command at all
            return;
        }

        Step currentStep = null;
        if (cmd instanceof StepCommand) {
            currentStep = ((StepCommand<?>) cmd).getStep();
        }

        // create the context explicitly
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        Context ctx = contextFactory.create(runtime, state, threadId, currentStep);

        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        value = ee.eval(ecf.global(ctx), value, Serializable.class);

        // prepare items
        // store items in an ArrayList because it is Serializable
        ArrayList<Serializable> items;
        if (value == null) {
            // value is null, not going to run the wrapped command at all
            return;
        } else if (value instanceof Collection) {
            Collection<Serializable> v = (Collection<Serializable>) value;
            if (v.isEmpty()) {
                // no items, nothing to do
                return;
            }

            items = new ArrayList<>(v);
        } else if (value instanceof Map) {
            Map<Serializable, Serializable> m = (Map<Serializable, Serializable>) value;
            items = m.entrySet().stream()
                    .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()))
                    .collect(Collectors.toCollection(ArrayList::new));
        } else if (value.getClass().isArray()) {
            items = new ArrayList<>(Arrays.asList((Serializable[]) value));
        } else {
            throw new IllegalArgumentException("'withItems' accepts only Lists of items, Java Maps or arrays of values. Got: " + value.getClass());
        }

        items.forEach(WithItemsWrapper::assertItem);

        if (items.isEmpty()) {
            return;
        }

        eval(state, threadId, items);
    }

    protected abstract void eval(State state, ThreadId threadId, ArrayList<Serializable> items);

    static void assertItem(Object item) {
        if (item == null) {
            return;
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())) {
            oos.writeObject(item);
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't use non-serializable values in 'withItems': " + item + " (" + item.getClass() + ")");
        }
    }

    static class ParallelWithItems extends WithItemsWrapper {

        private static final long serialVersionUID = 1L;
        private final Step step;

        protected ParallelWithItems(Command cmd, WithItems withItems, Collection<String> outVariables, Step step) {
            super(cmd, withItems, outVariables);
            this.step = step;
        }

        @Override
        protected void eval(State state, ThreadId threadId, ArrayList<Serializable> items) {
            Frame frame = state.peekFrame(threadId);

            // target frame for out variables
            Frame targetFrame = VMUtils.assertNearestRoot(state, threadId);

            List<Map.Entry<ThreadId, Serializable>> forks = items.stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(state.nextThreadId(), e))
                    .collect(Collectors.toList());

            for (int i = 0; i < forks.size(); i++) {
                Map.Entry<ThreadId, Serializable> f = forks.get(i);

                Frame cmdFrame = Frame.builder()
                        .nonRoot()
                        .commands()
                        .build();

                cmdFrame.setLocal(CURRENT_ITEMS, items);
                cmdFrame.setLocal(CURRENT_INDEX, i);
                cmdFrame.setLocal(CURRENT_ITEM, f.getValue());

                // fork will create rootFrame for forked commands
                Command itemCmd = new ForkCommand(f.getKey(),
                        new AppendVariablesCommand(outVariables, null, targetFrame),
                        cmd);
                cmdFrame.push(itemCmd);

                state.pushFrame(threadId, cmdFrame);
            }

            state.pushFrame(threadId, Frame.builder()
                    .commands(new PrepareOutVariables(outVariables, targetFrame))
                    .nonRoot()
                    .build());

            frame.push(new JoinCommand<>(forks.stream().map(Map.Entry::getKey).collect(Collectors.toSet()), step));
        }
    }

    /**
     * Wraps a command into a loop specified by {@code withItems} option.
     * Creates a new call frame and keeps the item list, the current item
     * and the index as frame-local variables.
     */
    static class SerialWithItems extends WithItemsWrapper {

        private static final long serialVersionUID = 1L;

        protected SerialWithItems(Command cmd, WithItems withItems, Collection<String> outVariables) {
            super(cmd, withItems, outVariables);
        }

        @Override
        protected void eval(State state, ThreadId threadId, ArrayList<Serializable> items) {
            Frame loop = Frame.builder()
                    .nonRoot()
                    .build();

            loop.setLocal(CURRENT_ITEMS, items);
            loop.setLocal(CURRENT_INDEX, 0);
            loop.setLocal(CURRENT_ITEM, items.get(0));

            loop.push(new WithItemsNext(outVariables, cmd)); // next iteration

            Frame cmdFrame = Frame.builder()
                    .commands(cmd)
                    .root()
                    .build();

            Frame targetFrame = VMUtils.assertNearestRoot(state, threadId);
            loop.push(new AppendVariablesCommand(outVariables, cmdFrame, targetFrame));
            loop.push(new PrepareOutVariables(outVariables, targetFrame));

            state.pushFrame(threadId, loop);
            state.pushFrame(threadId, cmdFrame);
        }
    }

    static class WithItemsNext implements Command {

        private static final long serialVersionUID = 1L;

        private final Collection<String> outVariables;
        private final Command cmd;

        public WithItemsNext(Collection<String> outVariables, Command cmd) {
            this.outVariables = outVariables;
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

            loop.push(new WithItemsNext(outVariables, cmd)); // next iteration

            // frame wrapped command
            Frame cmdFrame = Frame.builder()
                    .commands(cmd)
                    .root()
                    .build();

            Frame targetFrame = VMUtils.assertNearestRoot(state, threadId);
            loop.push(new AppendVariablesCommand(outVariables, cmdFrame, targetFrame));

            state.pushFrame(threadId, cmdFrame);
        }
    }

    static class PrepareOutVariables implements Command {

        private static final long serialVersionUID = 1L;

        private final Collection<String> outVars;
        private final Frame targetFrame;

        private PrepareOutVariables(Collection<String> outVars, Frame targetFrame) {
            this.outVars = outVars;
            this.targetFrame = targetFrame;
        }

        @Override
        public void eval(Runtime runtime, State state, ThreadId threadId) {
            Frame frame = state.peekFrame(threadId);
            frame.pop();

            if (outVars.isEmpty()) {
                return;
            }

            for (String outVar : outVars) {
                VMUtils.putLocal(targetFrame, outVar, new ArrayList<>());
            }
        }
    }

    /**
     * Appends values of the specified variables from the source frame into
     * list variables in the target frame.
     */
    static class AppendVariablesCommand implements Command {

        private static final long serialVersionUID = 1L;

        private final Collection<String> variables;
        private final Frame sourceFrame;
        private final Frame targetFrame;

        public AppendVariablesCommand(Collection<String> variables, Frame sourceFrame, Frame targetFrame) {
            this.variables = variables;
            this.sourceFrame = sourceFrame;
            this.targetFrame = targetFrame;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void eval(Runtime runtime, State state, ThreadId threadId) {
            Frame frame = state.peekFrame(threadId);
            frame.pop();

            if (variables.isEmpty()) {
                return;
            }

            Frame effectiveSourceFrame = sourceFrame != null ? sourceFrame : VMUtils.assertNearestRoot(state, threadId);

            for (String v : variables) {
                // make sure we're not modifying the same list concurrently
                synchronized (targetFrame) {
                    ArrayList<Serializable> results = (ArrayList<Serializable>) targetFrame.getLocal(v);
                    Serializable result = null;
                    if (effectiveSourceFrame.hasLocal(v)) {
                        result = effectiveSourceFrame.getLocal(v);
                    }
                    results.add(result);
                }
            }
        }
    }
}
