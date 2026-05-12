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
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.util.*;

public class JoinCommand<T extends Step> extends StepCommand<T> {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(JoinCommand.class);
    private static final long BUSY_WAIT_SLEEP = 100; // how often poll status of threads, milliseconds

    private final Collection<ThreadId> ids;

    public JoinCommand(Collection<ThreadId> ids, T step) {
        super(step);

        this.ids = ids;
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        // Here's a very dumb but working solution to the problem
        // of monitoring child thread state - just a loop
        // waiting on a monitor . On each iteration it decides whether
        // the join command can be removed from the stack (and thus
        // continuing the execution) or not.
        // We could've used futures instead, but it's way more
        // complicated - especially when suspend/resume are involved.

        var finalStatuses = waitForChildren(state);
        var failed = finalStatuses.entrySet().stream()
                .filter(e -> e.getValue() == ThreadStatus.FAILED)
                .map(Map.Entry::getKey)
                .toList();
        if (!failed.isEmpty()) {
            throw new ParallelExecutionException(failed.stream()
                    .map(state::clearThreadError)
                    .filter(Objects::nonNull)
                    .toList());
        }

        if (finalStatuses.containsValue(ThreadStatus.SUSPENDED)) {
            log.trace("eval [{}] -> children suspended, suspending parent", threadId);
            state.setStatus(threadId, ThreadStatus.SUSPENDED);
            return;
        }

        state.peekFrame(threadId).pop();
    }

    private Map<ThreadId, ThreadStatus> waitForChildren(State state) {
        while (true) {
            var allStatuses = state.threadStatus();

            var targets = new HashMap<ThreadId, ThreadStatus>();
            var active = false;
            for (var id : ids) {
                // null means the thread completed and was removed by gc()
                var s = allStatuses.getOrDefault(id, ThreadStatus.DONE);
                targets.put(id, s);
                if (s == ThreadStatus.READY || s == ThreadStatus.UNWINDING) {
                    active = true;
                }
            }

            if (!active) {
                return targets;
            }

            try {
                Thread.sleep(BUSY_WAIT_SLEEP);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("JoinCommand interrupted", e);
            }
        }
    }
}
