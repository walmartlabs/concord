package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.common.ExceptionUtils;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.model.AbstractStep;
import com.walmartlabs.concord.runtime.v2.model.Location;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.runner.tasks.ContextProvider;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.el.ELException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private final UUID correlationId;

    private LogContext logContext;

    protected StepCommand(T step) {
        this(UUID.randomUUID(), step);
    }

    protected StepCommand(T step, LogContext logContext) {
        this(UUID.randomUUID(), step, logContext);
    }

    protected StepCommand(UUID correlationId, T step) {
        this(correlationId, step, null);
    }

    protected StepCommand(UUID correlationId, T step, LogContext logContext) {
        this.step = step;
        this.correlationId = correlationId;
        this.logContext = logContext;
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

        logContext = getLogContext(runtime, ctx, correlationId);
        if (logContext == null) {
            ContextProvider.withContext(ctx, () -> execute(runtime, state, threadId));
        } else {
            runtime.getService(RunnerLogger.class).withLogContext(logContext,
                    () -> ContextProvider.withContext(ctx, () -> execute(runtime, state, threadId)));
        }
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    @Override
    public void onException(Runtime runtime, Exception e, State state, ThreadId threadId) {
        if (step.getLocation() == null) {
            return;
        }

        if (logContext == null) {
            logException(e, state, threadId);
        } else {
            runtime.getService(RunnerLogger.class).withLogContext(logContext,
                    () -> logException(e, state, threadId));
        }
    }

    private void logException(Exception e, State state, ThreadId threadId) {
        log.error("{} {}", Location.toErrorPrefix(step.getLocation()), getExceptionMessage(e));
        List<StackTraceItem> stackTrace = state.getStackTrace(threadId);
        if (!stackTrace.isEmpty()) {
            log.error("Call stack:\n{}", stackTrace.stream().map(StackTraceItem::toString).collect(Collectors.joining("\n")));
        }
    }

    protected abstract void execute(Runtime runtime, State state, ThreadId threadId);

    protected LogContext getLogContext() {
        return logContext;
    }

    private LogContext getLogContext(Runtime runtime, Context ctx, UUID correlationId) {
        if (logContext != null) {
            return logContext;
        }

        String segmentName = getSegmentName(ctx, getStep());
        if (segmentName == null) {
            return null;
        }

        return buildLogContext(runtime, segmentName, correlationId);
    }

    private LogContext buildLogContext(Runtime runtime, String segmentName, UUID correlationId) {
        RunnerConfiguration runnerCfg = runtime.getService(RunnerConfiguration.class);
        boolean redirectSystemOutAndErr = runnerCfg.logging().sendSystemOutAndErrToSLF4J();

        return LogContext.builder()
                .segmentName(segmentName)
                .correlationId(correlationId)
                .redirectSystemOutAndErr(redirectSystemOutAndErr)
                .logLevel(getLogLevel(step))
                .segmentId(runtime.getService(RunnerLogger.class).createSegment(segmentName, correlationId))
                .build();
    }

    protected String getSegmentName(Context ctx, T step) {
        if (step instanceof AbstractStep) {
            String rawSegmentName = SegmentedLogger.getSegmentName((AbstractStep<?>) step);
            String segmentName = ctx.eval(rawSegmentName, String.class);
            if (segmentName != null) {
                return segmentName;
            }
        }

        return getDefaultSegmentName();
    }

    protected String getDefaultSegmentName() {
        return null;
    }

    protected Level getLogLevel(T step) {
        if (step instanceof AbstractStep) {
            return SegmentedLogger.getLogLevel((AbstractStep<?>) step);
        }

        return Level.INFO;
    }

    private static String getExceptionMessage(Exception e) {
        UserDefinedException u = ExceptionUtils.filterException(e, UserDefinedException.class);
        if (u != null) {
            return u.getMessage();
        }

        if (e instanceof ELException) {
            return
                ExceptionUtils.getExceptionList(e).stream()
                .map(Throwable::getMessage)
                .collect(Collectors.joining(". "));
        }

        List<Throwable> exceptions = ExceptionUtils.getExceptionList(e);
        return exceptions.get(exceptions.size() - 1).getMessage();
    }
}
