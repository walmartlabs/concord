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
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger.SYSTEM_SEGMENT_NAME;

public class LogSegmentScopeCommand<T extends AbstractStep<?>> extends StepCommand<T> {

    private static final long serialVersionUID = 1L;

    private final Command cmd;

    public LogSegmentScopeCommand(UUID correlationId, Command cmd, T step) {
        super(correlationId, step);

        this.cmd = cmd;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
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
                .locals((Map)frame.getLocals())
                .build();

        state.pushFrame(threadId, scopeFrame);
    }

    private LogContext getLogContext(Runtime runtime, Context ctx, UUID correlationId) {
        String segmentName = getSegmentName(ctx, getStep());
        if (segmentName == null) {
            return null;
        }

        return buildLogContext(ctx, runtime, segmentName, correlationId);
    }

    private LogContext buildLogContext(Context ctx, Runtime runtime, String segmentName, UUID correlationId) {
        RunnerConfiguration runnerCfg = runtime.getService(RunnerConfiguration.class);
        boolean redirectSystemOutAndErr = runnerCfg.logging().sendSystemOutAndErrToSLF4J();

        return LogContext.builder()
                .segmentName(segmentName)
                .correlationId(correlationId)
                .redirectSystemOutAndErr(redirectSystemOutAndErr)
                .logLevel(getLogLevel(getStep()))
                .segmentId(runtime.getService(RunnerLogger.class).createSegment(segmentName, correlationId, (Long)ctx.variables().get("parentSegmentId"), getSegmentMeta(getStep())))
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
        } else if (step instanceof Expression) {
            return SYSTEM_SEGMENT_NAME;
        } if (step instanceof FlowCall flowCall) {
            return ctx.eval(flowCall.getFlowName(), String.class);
        }

        return null;
    }

    private static Map<String, Object> getSegmentMeta(AbstractStep<?> step) {
        if (step instanceof TaskCall) {
            return Map.of("type", "task");
        } else if (step instanceof FlowCall) {
            Map<String, Object> result = new HashMap<>();
            result.put("type", "call");

            String rawSegmentName = SegmentedLogger.getSegmentName(step);
            if (rawSegmentName == null) {
                result.put("generated", true);
            }

            return result;
        }
        return Map.of();
    }

    private static Level getLogLevel(AbstractStep<?> step) {
        return SegmentedLogger.getLogLevel(step);
    }
}