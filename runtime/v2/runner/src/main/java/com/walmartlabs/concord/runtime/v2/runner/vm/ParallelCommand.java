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
import com.walmartlabs.concord.svm.commands.Fork;
import com.walmartlabs.concord.svm.commands.Join;

import java.util.*;
import java.util.stream.Collectors;

public class ParallelCommand extends StepCommand<ParallelBlock> {

    private static final long serialVersionUID = 1L;

    private final List<Command> commands;

    public ParallelCommand(List<Command> commands, ParallelBlock step) {
        super(step);
        this.commands = commands;
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        // parallel execution consist of "forks" for each command and a combined "join"

        List<Map.Entry<ThreadId, Command>> forks = commands.stream()
                .map(e -> new AbstractMap.SimpleEntry<>(state.nextThreadId(), e))
                .collect(Collectors.toList());

        Collection<ThreadId> forkIds = forks.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
        frame.push(new Join(forkIds));

        Collections.reverse(forks);
        forks.forEach(f -> frame.push(new Fork(f.getKey(), f.getValue())));
    }
}
