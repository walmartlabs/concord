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

import ch.qos.logback.classic.Level;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.model.AbstractStep;
import com.walmartlabs.concord.runtime.v2.model.Location;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.runner.tasks.ContextProvider;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Command;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Base class for commands that were created from a flow {@link Step}.
 * <p/>
 * Subclasses must implement the {@link #execute(Runtime, State, ThreadId)} method.
 * <p/>
 * Subclasses can optionally implement {@link #getSegmentName(Context, Step)} to
 * enable "segmented logging" for the duration of their execution.
 */
public abstract class StepCommand<T extends Step> implements Command {

    private static final Logger log = LoggerFactory.getLogger(StepCommand.class);

    private static final long serialVersionUID = 1L;

    private final T step;

    protected StepCommand(T step) {
        this.step = step;
    }

    public T getStep() {
        return step;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);

        T step = getStep();
        UUID correlationId = getCorrelationId();
        Context ctx = contextFactory.create(runtime, state, threadId, step, correlationId);

        String segmentName = getSegmentName(ctx, step);

        if (segmentName == null) {
            executeWithContext(ctx, runtime, state, threadId);
        } else {
            RunnerConfiguration runnerCfg = runtime.getService(RunnerConfiguration.class);
            boolean redirectSystemOutAndErr = runnerCfg.logging().sendSystemOutAndErrToSLF4J();
            LogContext logContext = LogContext.builder()
                    .segmentName(segmentName)
                    .segmentId(correlationId.toString())
                    .redirectSystemOutAndErr(redirectSystemOutAndErr)
                    .logLevel(getLogLevel(step))
                    .build();
            SegmentedLogger.withLogSegment(logContext,
                    () -> executeWithContext(ctx, runtime, state, threadId));
        }
    }

    protected UUID getCorrelationId() {
        return UUID.randomUUID();
    }

    private void executeWithContext(Context ctx, Runtime runtime, State state, ThreadId threadId) {
        ContextProvider.withContext(ctx, () -> {
            try {
                execute(runtime, state, threadId);
            } catch (Exception e) {
                if (step.getLocation() == null) {
                    throw e;
                }

                log.error("{} {}", Location.toErrorPrefix(step.getLocation()), e.getMessage());
                throw e;
            }
        });
    }

    protected abstract void execute(Runtime runtime, State state, ThreadId threadId);

    protected String getSegmentName(Context ctx, T step) {
        if (step instanceof AbstractStep) {
            return SegmentedLogger.getSegmentName((AbstractStep<?>) step);
        }

        return null;
    }

    protected Level getLogLevel(T step) {
        if (step instanceof AbstractStep) {
            return SegmentedLogger.getLogLevel((AbstractStep<?>) step);
        }

        return Level.INFO;
    }
}
