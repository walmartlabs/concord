package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.ParallelBlock;
import com.walmartlabs.concord.runtime.v2.model.ParallelBlockOptions;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ParallelCommand extends StepCommand<ParallelBlock> {

    private static final long serialVersionUID = 1L;

    private final List<Command> commands;

    public ParallelCommand(ParallelBlock step, List<Command> commands) {
        super(step);
        this.commands = commands;
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        // parallel execution consist of "forks" for each command running in separate threads
        // and a combined "join" executing in the parent (current) thread

        List<Map.Entry<ThreadId, Command>> forks = commands.stream()
                .map(e -> new AbstractMap.SimpleEntry<>(state.nextThreadId(), e))
                .collect(Collectors.toList());

        ParallelBlockOptions opts = Objects.requireNonNull(getStep().getOptions());

        Command outVarsCommand;
        if (!opts.outExpr().isEmpty()) {
            Map<String, Object> accumulator = new ConcurrentHashMap<>();
            outVarsCommand = new CollectVariablesCommand(accumulator);

            frame.push(new EvalVariablesCommand(runtime.getService(Context.class), accumulator, opts.outExpr(), frame));
        } else {
            outVarsCommand = new CopyVariablesCommand(opts.out(), null, frame);
        }

        Collection<ThreadId> forkIds = forks.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
        frame.push(new JoinCommand(forkIds));

        Collections.reverse(forks);
        forks.forEach(f -> {
            Command cmd = new ForkCommand(f.getKey(), outVarsCommand, f.getValue());
            frame.push(cmd);
        });
    }

    static class CollectVariablesCommand implements Command {

        private final Map<String, Object> accumulator;

        public CollectVariablesCommand(Map<String, Object> accumulator) {
            this.accumulator = accumulator;
        }

        @Override
        public void eval(Runtime runtime, State state, ThreadId threadId) {
            Frame frame = state.peekFrame(threadId);
            frame.pop();

            accumulator.putAll(frame.getLocals());
        }
    }

    static class EvalVariablesCommand implements Command {

        private final Context ctx;
        private final Map<String, Object> allVars;
        private final Map<String, Serializable> variables;
        private final Frame target;

        public EvalVariablesCommand(Context ctx, Map<String, Object> allVars, Map<String, Serializable> variables, Frame target) {
            this.ctx = ctx;
            this.allVars = allVars;
            this.variables = variables;
            this.target = target;
        }

        @Override
        public void eval(Runtime runtime, State state, ThreadId threadId) {
            Frame frame = state.peekFrame(threadId);
            frame.pop();

            ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
            Map<String, Serializable> out = expressionEvaluator.evalAsMap(EvalContextFactory.global(ctx, allVars), variables);
            out.forEach((k, v) -> VMUtils.putLocal(target, k, v));
        }
    }
}
