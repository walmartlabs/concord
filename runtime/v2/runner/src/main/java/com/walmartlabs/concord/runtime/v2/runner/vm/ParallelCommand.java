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
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serializable;
import java.util.*;
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

        List<String> outVars = new ArrayList<>(getStep().getOptions().out());

        Collection<ThreadId> forkIds = forks.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
        frame.push(new JoinCommand(forkIds));

        Collections.reverse(forks);
        forks.forEach(f -> {
            // each new frame executes it's own copy of ProcessOutVariablesCommand after the user's command is completed
            Command cmd = new ForkCommand(f.getKey(), new ProcessOutVariablesCommand(threadId, outVars), f.getValue());
            frame.push(cmd);
        });
    }

    /**
     * Copies specified variables from the current thread into the specified target thread.
     */
    public static class ProcessOutVariablesCommand implements Command {

        private static final long serialVersionUID = 1L;

        private final ThreadId targetThreadId;
        private final List<String> outVars;

        public ProcessOutVariablesCommand(ThreadId targetThreadId, List<String> outVars) {
            this.targetThreadId = targetThreadId;
            this.outVars = outVars;
        }

        @Override
        public void eval(Runtime runtime, State state, ThreadId threadId) {
            Frame frame = state.peekFrame(threadId);
            frame.pop();

            for (String k : outVars) {
                // we assume that the current frame is the root frame of the thread
                // if it wasn't the case we'd need to call VMUtils#getCombinedLocal
                if (!frame.hasLocal(k)) {
                    continue;
                }

                Serializable v = frame.getLocal(k);
                VMUtils.putLocal(state, targetThreadId, k, v);
            }
        }
    }
}
