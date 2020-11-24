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
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Wraps a command into a loop specified by {@code withItems} option.
 * Creates a new call frame and keeps the item list, the current item
 * and the index as frame-local variables.
 */
public class WithItemsWrapper implements Command {

    private static final long serialVersionUID = 1L;

    // TODO move into the actual Constants
    public static final String CURRENT_ITEMS = "items";
    public static final String CURRENT_INDEX = "itemIndex";
    public static final String CURRENT_ITEM = "item";

    private final Command cmd;
    private final WithItems withItems;
    private final List<String> outVariables;

    public WithItemsWrapper(Command cmd, WithItems withItems, List<String> outVariables) {
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

        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        value = ee.eval(EvalContextFactory.global(ctx), value, Serializable.class);

        // prepare items
        // store items in an ArrayList because it is Serializable
        ArrayList<Object> items;
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
                    .map(e -> new SerializableEntry(e.getKey(), e.getValue()))
                    .collect(Collectors.toCollection(ArrayList::new));
        } else if (value.getClass().isArray()) {
            items = new ArrayList<>(Arrays.asList((Serializable[]) value));
        } else {
            throw new IllegalArgumentException("'withItems' accepts only Lists of items, Java Maps or arrays of values. Got: " + value.getClass());
        }

        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(0);
            if (item == null || item instanceof Serializable) {
                continue;
            }

            throw new IllegalStateException("Can't use non-serializable values in 'withItems': " + item + " (" + item.getClass() + ")");
        }

        Frame loop = Frame.builder()
                .nonRoot()
                .build();

        loop.setLocal(CURRENT_ITEMS, items);
        loop.setLocal(CURRENT_INDEX, 0);
        loop.setLocal(CURRENT_ITEM, (Serializable) items.get(0));

        loop.push(new WithItemsNext(outVariables, cmd)); // next iteration

        Frame cmdFrame = Frame.builder()
                .commands(cmd)
                .root()
                .build();

        Frame targetFrame = VMUtils.assertNearestRoot(state, threadId);
        loop.push(new AppendVariablesCommand(outVariables, cmdFrame, targetFrame));
        loop.push(new PrepareOutVariables(outVariables));

        state.pushFrame(threadId, loop);
        state.pushFrame(threadId, cmdFrame);
    }

    public static class WithItemsNext implements Command {

        private static final long serialVersionUID = 1L;

        private final List<String> outVariables;
        private final Command cmd;

        public WithItemsNext(List<String> outVariables, Command cmd) {
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

            state.pushFrame(threadId, loop);
            state.pushFrame(threadId, cmdFrame);
        }
    }

    private static class PrepareOutVariables implements Command {

        private static final long serialVersionUID = 1L;

        private final List<String> outVars;

        private PrepareOutVariables(List<String> outVars) {
            this.outVars = outVars;
        }

        @Override
        public void eval(Runtime runtime, State state, ThreadId threadId) {
            Frame outerFrame = state.peekFrame(threadId);
            outerFrame.pop();

            if (outVars.isEmpty()) {
                return;
            }

            for (String outVar : outVars) {
                VMUtils.putLocal(state, threadId, outVar, new ArrayList<>());
            }
        }
    }

    /**
     * Appends values of the specified variables from the source frame into
     * list variables in the target frame.
     */
    private static class AppendVariablesCommand implements Command {

        private static final long serialVersionUID = 1L;

        private final List<String> variables;
        private final Frame sourceFrame;
        private final Frame targetFrame;

        public AppendVariablesCommand(List<String> variables, Frame sourceFrame, Frame targetFrame) {
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

            for (String v : variables) {
                ArrayList<Serializable> results = (ArrayList<Serializable>) targetFrame.getLocal(v);
                Serializable result = null;
                if (sourceFrame.hasLocal(v)) {
                    result = sourceFrame.getLocal(v);
                }
                results.add(result);
            }
        }
    }

    private static class SerializableEntry implements Map.Entry<Serializable, Serializable>, Serializable {

        private static final long serialVersionUID = 1L;

        private final Serializable key;
        private final Serializable value;

        public SerializableEntry(Serializable key, Serializable value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Serializable getKey() {
            return key;
        }

        @Override
        public Serializable getValue() {
            return value;
        }

        @Override
        public Serializable setValue(Serializable value) {
            throw new IllegalStateException("Can't modify an immutable entry. Tried to set '" + key + "' to '" + value + "'");
        }

        @Override
        public final String toString() {
            return "[" + key + " -> " + value + "]";
        }
    }
}
