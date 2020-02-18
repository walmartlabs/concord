package com.walmartlabs.concord.svm;

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

import com.walmartlabs.concord.svm.commands.PopFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;

public class VM {

    private static final Logger log = LoggerFactory.getLogger(VM.class);

    private final RuntimeFactory runtimeFactory;
    private final Collection<ExecutionListener> listeners;

    public VM(RuntimeFactory runtimeFactory, Collection<ExecutionListener> listeners) {
        this.runtimeFactory = runtimeFactory;
        this.listeners = new ArrayList<>(listeners);
    }

    /**
     * Starts the execution using the provided state.
     */
    public void start(State state) throws Exception {
        log.debug("start -> start");

        Runtime runtime = runtimeFactory.create(this);
        eval(runtime, state, state.getRootThreadId());

        log.debug("start -> done");
    }

    /**
     * Resumes the execution using the provided state and an event reference.
     */
    public void resume(State state, String eventRef) throws Exception {
        log.debug("resume ['{}'] -> start", eventRef);

        ThreadId eventThreadId = state.removeEventRef(eventRef);
        if (eventThreadId == null) {
            throw new IllegalStateException("Can't find eventRef: " + eventRef);
        }

        Runtime rt = runtimeFactory.create(this);

        // start all forked threads
        wakeDependencies(rt, state, eventThreadId);

        // run the root thread in the caller's (Java) thread
        ThreadId rootThreadId = state.getRootThreadId();
        state.setStatus(rootThreadId, ThreadStatus.READY);
        eval(rt, state, rootThreadId);

        log.debug("resume ['{}'] -> done", eventRef);
    }

    /**
     * Executes a single command using the provided state.
     * Doesn't firing any {@link #listeners} and doesn't unwind the stack in
     * case or errors.
     */
    public void run(State state, Command cmd) throws Exception {
        log.debug("run ['{}'] -> start", cmd);

        Runtime rt = runtimeFactory.create(this);
        ThreadId threadId = state.getRootThreadId();
        cmd.eval(rt, state, threadId);

        log.debug("run ['{}'] -> done", cmd);
    }

    public void eval(Runtime runtime, State state, ThreadId threadId) throws Exception {
        try {
            while (true) {
                ThreadStatus status = state.getStatus(threadId);
                if (status == ThreadStatus.DONE) {
                    throw new IllegalStateException("The thread is already complete: " + threadId);
                } else if (status == ThreadStatus.SUSPENDED) {
                    log.trace("eval [{}] -> the thread is suspended, stopping the execution", threadId);
                    break;
                } else if (status == ThreadStatus.FAILED) {
                    throw new IllegalStateException("The thread has failed, can't continue the exection: " + threadId);
                }

                Frame frame = state.peekFrame(threadId);
                if (frame == null) {
                    log.trace("eval [{}] -> the thread is done, stopping the execution", threadId);
                    state.setStatus(threadId, ThreadStatus.DONE);
                    break;
                }

                Command cmd = frame.peek();
                if (cmd == null) {
                    log.trace("eval [{}] -> the frame is complete", threadId);
                    state.popFrame(threadId);
                    continue;
                }

                try {
                    fireBeforeCommand(runtime, state, threadId, cmd);
                    cmd.eval(runtime, state, threadId);
                    fireAfterCommand(runtime, state, threadId, cmd);
                } catch (Exception e) {
                    unwind(state, threadId, e);
                }
            }
        } finally {
            state.gc();
        }
    }

    private void unwind(State state, ThreadId threadId, Exception cause) throws Exception {
        while (true) {
            Frame frame = state.peekFrame(threadId);
            if (frame == null) {
                // no more frames to unwind, looks like there was no exception handler
                state.setStatus(threadId, ThreadStatus.FAILED);
                log.error("Unhandled exception in the SVM thread {}: {}", threadId, cause.getMessage());
                state.setThreadError(threadId, cause);
                throw cause;
            }

            Command handler = frame.getExceptionHandler();
            if (handler != null) {
                // avoid issues with exceptions throws in exception handlers
                frame.clearExceptionHandler();

                // save the exception as a local frame variable, so it can be retrieved
                // by the error handling core
                frame.setLocal(Frame.LAST_EXCEPTION_KEY, cause);

                // remove the current frame after the error handling code is done
                frame.push(new PopFrame());

                // and run the error handler next
                frame.push(handler);

                break;
            }

            // unwinding
            state.popFrame(threadId);
        }
    }

    private void wakeDependencies(Runtime rt, State state, ThreadId id) {
        ThreadId rootId = state.getRootThreadId();

        // go though the tree of threads, starting from the suspended thread
        while (id != null && !rootId.equals(id)) {
            ThreadStatus status = state.getStatus(id);
            if (status == ThreadStatus.SUSPENDED) {
                state.setStatus(id, ThreadStatus.READY);
                rt.spawn(state, id);
            }

            id = state.getParentThreadId(id);
        }
    }

    private void fireBeforeCommand(Runtime runtime, State state, ThreadId threadId, Command cmd) {
        for (ExecutionListener l : listeners) {
            l.beforeCommand(runtime, this, state, threadId, cmd);
        }
    }

    private void fireAfterCommand(Runtime runtime, State state, ThreadId threadId, Command cmd) {
        for (ExecutionListener l : listeners) {
            l.afterCommand(runtime, this, state, threadId, cmd);
        }
    }
}
