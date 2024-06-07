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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.walmartlabs.concord.svm.ExecutionListener.Result.BREAK;

public class VM {

    private static final Logger log = LoggerFactory.getLogger(VM.class);

    private final RuntimeFactory runtimeFactory;
    private final ExecutionListenerHolder listeners;

    public VM(RuntimeFactory runtimeFactory, Collection<ExecutionListener> listeners) {
        this.runtimeFactory = runtimeFactory;
        this.listeners = new ExecutionListenerHolder(this, listeners);
    }

    /**
     * Starts the execution using the provided state.
     */
    public void start(State state) throws Exception {
        log.debug("start -> start");

        listeners.fireBeforeProcessStart();

        Runtime runtime = runtimeFactory.create(this);
        EvalResult result = execute(runtime, state);

        listeners.fireAfterProcessEnds(runtime, state, result.lastFrame);

        log.debug("start -> done");
    }

    /**
     * Resumes the execution using the provided state and an event reference.
     */
    public void resume(State state, Set<String> eventRefs) throws Exception {
        log.debug("resume ['{}'] -> start", eventRefs);

        eventRefs.forEach(eventRef -> {
            ThreadId eventThreadId = state.removeEventRef(eventRef);
            if (eventThreadId == null) {
                throw new IllegalStateException("Can't find eventRef: " + eventRef);
            }
        });

        wakeSuspended(state);

        listeners.fireBeforeProcessResume();

        Runtime runtime = runtimeFactory.create(this);
        EvalResult result = execute(runtime, state);

        listeners.fireAfterProcessEnds(runtime, state, result.lastFrame);

        log.debug("resume ['{}'] -> done", eventRefs);
    }

    /**
     * Executes a single command using the provided state.
     * Doesn't fire any {@link #listeners} and doesn't unwind the stack in
     * case or errors.
     */
    public void run(State state, Command cmd) throws Exception {
        log.debug("run ['{}'] -> start", cmd);

        Runtime rt = runtimeFactory.create(this);
        ThreadId threadId = state.getRootThreadId();
        cmd.eval(rt, state, threadId);

        log.debug("run ['{}'] -> done", cmd);
    }

    public EvalResult eval(Runtime runtime, State state, ThreadId threadId) throws Exception {
        Frame lastFrame = null;

        try {
            while (true) {
                ThreadStatus status = state.getStatus(threadId);

                if (status == ThreadStatus.DONE) {
                    throw new IllegalStateException("The thread is already complete: " + threadId);
                } else if (status == ThreadStatus.SUSPENDED) {
                    log.trace("eval [{}] -> the thread is suspended, stopping the execution", threadId);
                    break;
                } else if (status == ThreadStatus.FAILED) {
                    throw new IllegalStateException("The thread has failed, can't continue the execution: " + threadId);
                }

                Frame frame = state.peekFrame(threadId);
                if (frame == null) {
                    log.trace("eval [{}] -> the thread is done, stopping the execution", threadId);
                    state.setStatus(threadId, ThreadStatus.DONE);
                    break;
                }

                lastFrame = frame;

                Command cmd = frame.peek();
                if (cmd == null) {
                    log.trace("eval [{}] -> the frame is complete", threadId);
                    frame.push(new PopFrameCommand());
                    continue;
                }

                boolean stop = false;
                try {
                    if (listeners.fireBeforeCommand(runtime, state, threadId, cmd) == BREAK) {
                        stop = true;
                    }

                    cmd.eval(runtime, state, threadId);

                    if (listeners.fireAfterCommand(runtime, state, threadId, cmd) == BREAK) {
                        stop = true;
                    }
                } catch (Exception e) {
                    unwind(runtime, state, threadId, e);
                }

                if (stop) {
                    break;
                }
            }
        } finally {
            state.gc();
        }

        return new EvalResult(lastFrame);
    }

    private EvalResult execute(Runtime runtime, State state) throws Exception {
        EvalResult result;

        while (true) {
            // if we're restoring from a previously saved state or we had new threads created
            // on the previous iteration we need to spawn all READY threads
            for (Map.Entry<ThreadId, ThreadStatus> e : state.threadStatus().entrySet()) {
                if (e.getKey() != state.getRootThreadId() && e.getValue() == ThreadStatus.READY) {
                    runtime.spawn(state, e.getKey());
                }
            }

            result = eval(runtime, state, state.getRootThreadId());

            if (listeners.fireAfterEval(runtime, state) == BREAK) {
                break;
            }

            wakeSuspended(state);

            if (listeners.fireAfterWakeUp(runtime, state) == BREAK) {
                break;
            }
        }

        return result;
    }

    private void unwind(Runtime runtime, State state, ThreadId threadId, Exception cause) throws Exception {
        while (true) {
            Frame frame = state.peekFrame(threadId);
            if (frame == null) {
                // no more frames to unwind, looks like there was no exception handler
                state.setStatus(threadId, ThreadStatus.FAILED);
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
                frame.push(new PopFrameCommand());

                // and run the error handler next
                frame.push(handler);

                break;
            }

            // unwinding
            if (frame.getFinallyHandler() != null) {
                frame.setLocal(Frame.LAST_EXCEPTION_KEY, cause);
                frame.push(frame.getFinallyHandler());
                frame.getFinallyHandler().eval(runtime, state, threadId);
            }
            state.popFrame(threadId, cmd -> cmd.onError(runtime, state, threadId, cause));
        }
    }

    private static void wakeSuspended(State state) {
        Map<ThreadId, String> events = state.getEventRefs();
        for (Map.Entry<ThreadId, ThreadStatus> e : state.threadStatus().entrySet()) {
            if (!events.containsKey(e.getKey()) && e.getValue() == ThreadStatus.SUSPENDED) {
                state.setStatus(e.getKey(), ThreadStatus.READY);
            }
        }
    }

    public static class EvalResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private final Frame lastFrame;

        private EvalResult(Frame lastFrame) {
            this.lastFrame = lastFrame;
        }
    }
}
