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
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogUtils;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskException;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;

import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.CallContext;
import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.Method;

/**
 * Calls the specified task. Responsible for preparing the task's input
 * and processing the output.
 */
public class LogSegmentScopeCommand<T extends Step> extends StepCommand<T> {

    private static final long serialVersionUID = 1L;

    private final Command cmd;

    public LogSegmentScopeCommand(UUID correlationId, Command cmd, T step) {
        super(correlationId, step);

        this.cmd = cmd;
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();


        Context ctx = runtime.getService(Context.class);
        LogContext logContext = getLogContext(runtime, ctx, getCorrelationId());
        if (logContext == null || logContext.segmentId() == null) {
            frame.push(cmd);
            return;
        }

        LogSegmentUtils.pushLogContext(threadId, state, logContext);

        Frame scopeFrame = Frame.builder()
                .nonRoot()
                .commands(cmd)
                .finallyHandler(new CloseLogSegmentCommand(getCorrelationId()))
                .build();

        state.pushFrame(threadId, scopeFrame);
    }

    private LogContext getLogContext(Runtime runtime, Context ctx, UUID correlationId) {
        String segmentName = getSegmentName(ctx, (AbstractStep<?>) getStep());
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
                .logLevel(getLogLevel((AbstractStep<?>) getStep()))
                .segmentId(runtime.getService(RunnerLogger.class).createSegment(segmentName, correlationId))
                .build();
    }

    private static String getSegmentName(Context ctx, AbstractStep<?> step) {
        String rawSegmentName = SegmentedLogger.getSegmentName(step);
        String segmentName = ctx.eval(rawSegmentName, String.class);
        if (segmentName != null) {
            return segmentName;
        }

        // default segment names...
        if (step instanceof TaskCall taskCall) {
            return "task: " + taskCall.getName();
        } else if (step instanceof ScriptCall scriptCall) {
            return "script: " + scriptCall.getLanguageOrRef();
        }

        return null;
    }

    private static Level getLogLevel(AbstractStep<?> step) {
        return SegmentedLogger.getLogLevel(step);
    }
}
