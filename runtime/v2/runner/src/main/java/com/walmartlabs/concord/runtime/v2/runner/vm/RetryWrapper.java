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
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Constants;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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

    private static final String RETRY_CFG = "__retry_cfg";

    private final Command cmd;
    private final Retry retry;

    public RetryWrapper(Command cmd, Retry retry) {
        this.cmd = cmd;
        this.retry = retry;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        execute(runtime, state, threadId);
    }

    @Override
    public void onException(Runtime runtime, Exception e, State state, ThreadId threadId) {
        cmd.onException(runtime, e, state, threadId);
    }

    private void execute(Runtime runtime, State state, ThreadId threadId) {
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

        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);

        int times = retry.times();
        if (retry.timesExpression() != null) {
            Number n = ee.eval(ecf.global(ctx), retry.timesExpression(), Number.class);
            if (n != null) {
                times = n.intValue();
            }
        }

        Duration delay = retry.delay();
        if (retry.delayExpression() != null) {
            Number n = ee.eval(ecf.global(ctx), retry.delayExpression(), Number.class);
            if (n != null) {
                delay = Duration.ofSeconds(n.longValue());
            }
        }

        inner.setLocal(Constants.Runtime.RETRY_ATTEMPT_NUMBER, 0);
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
            int attemptNo = (int) frame.getLocal(Constants.Runtime.RETRY_ATTEMPT_NUMBER);
            Throwable lastError = (Throwable) frame.getLocal(Frame.LAST_EXCEPTION_KEY);

            if (attemptNo >= retry.times()) {
                // no more attempts left, rethrow the last exception
                if (lastError instanceof RuntimeException) {
                    throw (RuntimeException) lastError;
                } else {
                    throw new RuntimeException(lastError);
                }
            }

            Duration delay = retry.delay();
            log.warn("Last error: {}. Waiting for {}ms before retry (attempt #{})", lastError, delay.toMillis(), attemptNo);

            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // set the same exception handler for the next attempt
            frame.setExceptionHandler(this);
            // update the attempt number
            frame.setLocal(Constants.Runtime.RETRY_ATTEMPT_NUMBER, attemptNo + 1);

            // override the task's "in" if needed
            if (retry.input() != null) {
                Map<String, Object> m = Collections.unmodifiableMap(Objects.requireNonNull(retry.input()));
                VMUtils.setInputOverrides(frame, m);
            }

            frame.push(cmd);
        }
    }
}
