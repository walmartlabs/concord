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
import java.util.Collection;
import java.util.Map;

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

        while (true) {
            Map<ThreadId, ThreadStatus> status = state.threadStatus();

            boolean allDone = status.entrySet().stream()
                    .map(e -> ids.contains(e.getKey()) ? e.getValue() : ThreadStatus.DONE)
                    .allMatch(e -> e == ThreadStatus.DONE);

            // all children are done, proceed with the execution
            if (allDone) {
                state.peekFrame(threadId).pop();
                return;
            }

            boolean anySuspended = anyMatch(status, ids, ThreadStatus.SUSPENDED);
            boolean anyReady = anyMatch(status, ids, ThreadStatus.READY);

            // all children are either DONE or SUSPENDED - suspend the parent execution
            if (!anyReady && anySuspended) {
                log.trace("eval [{}] -> some of the children are SUSPENDED, suspending the parent thread", threadId);
                state.setStatus(threadId, ThreadStatus.SUSPENDED);
                return;
            }

            // find if some of the threads has failed with an unhandled exception
            Collection<ThreadId> failed = status.entrySet().stream()
                    .filter(e -> e.getValue() == ThreadStatus.FAILED)
                    .map(Map.Entry::getKey)
                    .toList();

            // nothing left to run and we got some unhandled exceptions
            if (!failed.isEmpty() && !anyReady) {
                throw new ParallelExecutionException(failed.stream()
                        .map(state::clearThreadError)
                        .toList());
            }

            // some children are still running, wait for a bit and then check again
            try {
                Thread.sleep(BUSY_WAIT_SLEEP);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean anyMatch(Map<ThreadId, ThreadStatus> status, Collection<ThreadId> ids, ThreadStatus match) {
        return status.entrySet().stream()
                .filter(e -> ids.contains(e.getKey()))
                .anyMatch(e -> e.getValue() == match);
    }
}
