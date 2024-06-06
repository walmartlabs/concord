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
import com.walmartlabs.concord.runtime.v2.model.ScriptCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;

import java.io.Serial;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CreateLogSegmentCommand extends StepCommand<AbstractStep<?>> {

    @Serial
    private static final long serialVersionUID = 1L;

    public CreateLogSegmentCommand(UUID correlationId, AbstractStep<?> step) {
        super(correlationId, step);
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        Context ctx = runtime.getService(Context.class);

        LogContext logContext = getLogContext(runtime, ctx, getCorrelationId());
        LogSegmentUtils.pushLogContext(threadId, state, logContext);
    }

    @Override
    protected boolean isSegmented() {
        return false;
    }

    private LogContext getLogContext(Runtime runtime, Context ctx, UUID correlationId) {
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
                .logLevel(getLogLevel(getStep()))
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
