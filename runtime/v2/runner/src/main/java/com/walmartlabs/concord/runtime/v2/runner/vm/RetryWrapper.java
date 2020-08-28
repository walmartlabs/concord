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

import com.walmartlabs.concord.runtime.v2.model.Retry;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Wraps a command into a "retry" loop specified by {@code retry} option.
 * Creates a new call frame, installs it's own exception handler and re-runs
 * the command on error.
 */
public class RetryWrapper implements Command {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(RetryWrapper.class);

    private static final String RETRY_ATTEMPT_NUMBER = "__retry_attemptNo";
    private static final String RETRY_CFG = "__retry_cfg";

    private final Command cmd;
    private final Retry retry;

    public RetryWrapper(Command cmd, Retry retry) {
        this.cmd = cmd;
        this.retry = retry;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        // wrap the command into a frame with an exception handler
        Frame inner = Frame.builder()
                .nonRoot()
                .exceptionHandler(new NextRetry(cmd))
                .commands(cmd)
                .build();

        // create the context explicitly
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        Context ctx = contextFactory.create(runtime, state, threadId, getCurrentStep());

        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);

        int times = retry.times();
        if (retry.timesExpression() != null) {
            times = ee.eval(EvalContextFactory.global(ctx), retry.timesExpression(), Integer.class);
        }

        long delay = retry.delay();
        if (retry.delayExpression() != null) {
            delay = ee.eval(EvalContextFactory.global(ctx), retry.delayExpression(), Long.class);
        }

        inner.setLocal(RETRY_ATTEMPT_NUMBER, 0);
        inner.setLocal(RETRY_CFG, Retry.builder().from(retry)
                .times(times)
                .delay(delay)
                .build());

        state.pushFrame(threadId, inner);
    }

    private Step getCurrentStep() {
        if (cmd instanceof StepCommand) {
            return ((StepCommand<?>) cmd).getStep();
        }
        return null;
    }

    public static class NextRetry implements Command {

        private static final long serialVersionUID = 1L;

        private final Command cmd;

        public NextRetry(Command cmd) {
            this.cmd = cmd;
        }

        @Override
        public void eval(Runtime runtime, State state, ThreadId threadId) {
            Frame frame = state.peekFrame(threadId);
            frame.pop();

            Retry retry = (Retry) frame.getLocal(RETRY_CFG);
            int attemptNo = (int) frame.getLocal(RETRY_ATTEMPT_NUMBER);
            Throwable lastError = (Throwable) frame.getLocal(Frame.LAST_EXCEPTION_KEY);

            if (attemptNo >= retry.times()) {
                // no more attempts left, rethrow the last exception
                if (lastError instanceof RuntimeException) {
                    throw (RuntimeException) lastError;
                } else {
                    throw new RuntimeException(lastError);
                }
            }

            long delay = retry.delay();
            log.warn("Last error: {}. Waiting for {}ms before retry (attempt #{})", lastError, delay, attemptNo);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // set the same exception handler for the next attempt
            frame.setExceptionHandler(this);
            // update the attempt number
            frame.setLocal(RETRY_ATTEMPT_NUMBER, attemptNo + 1);

            // override the task's "in" if needed
            if (retry.input() != null) {
                Map<String, Object> m = Collections.unmodifiableMap(Objects.requireNonNull(retry.input()));
                VMUtils.putLocals(frame, m);
            }

            frame.push(cmd);
        }
    }
}
