package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.runtime.common.SensitiveDataMasker;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentKind;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.svm.Command;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import javax.annotation.Nullable;
import java.io.Serial;
import java.util.UUID;

/**
 * Opens a log segment owned by the current frame, see {@link LogSegmentUtils}.
 * <p>
 * The name is resolved before the segment is created and never changes. The step's
 * {@code name} is evaluated only where the step's body runs ({@code evaluateName}):
 * on the step itself, on each iteration of a loop or on each attempt of a retry.
 * Other segments get a literal {@code name} or a structural one, e.g. {@code task: http}
 * or {@code iteration #2}.
 */
public class OpenSegmentCommand implements Command {

    @Serial
    private static final long serialVersionUID = 1L;

    private final LogSegmentKind kind;
    private final Step step;
    private final UUID correlationId;
    private final Integer iterationIndex;
    private final Integer attemptIndex;
    private final boolean evaluateName;
    private final boolean keepOnHandler;

    public OpenSegmentCommand(LogSegmentKind kind, Step step, UUID correlationId, @Nullable Integer iterationIndex, @Nullable Integer attemptIndex,
                              boolean evaluateName, boolean keepOnHandler) {
        this.kind = kind;
        this.step = step;
        this.correlationId = correlationId;
        this.iterationIndex = iterationIndex;
        this.attemptIndex = attemptIndex;
        this.evaluateName = evaluateName;
        this.keepOnHandler = keepOnHandler;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        String name;
        try {
            name = resolveName(runtime, state, threadId);
        } catch (Exception e) {
            LogSegmentUtils.withLogContext(runtime, state, threadId,
                    () -> StepCommand.logException(step, state, threadId, e));
            throw e;
        }

        boolean redirect = runtime.getService(RunnerConfiguration.class).logging().sendSystemOutAndErrToSLF4J();
        Integer iteration = iterationIndex;
        if (kind == LogSegmentKind.ATTEMPT) {
            iteration = (Integer) VMUtils.getCombinedLocal(
                    state, threadId, LoopIterationCommand.iterationKey(correlationId));
        }
        LogSegmentUtils.open(runtime, state, threadId, kind, name, correlationId, iteration, attemptIndex,
                getLogLevel(state, threadId), redirect, keepOnHandler);
    }

    private String resolveName(Runtime runtime, State state, ThreadId threadId) {
        String raw = step instanceof AbstractStep<?> s ? SegmentedLogger.getSegmentName(s) : null;

        String name = null;
        if (raw != null && evaluateName) {
            Context ctx = runtime.getService(ContextFactory.class).create(runtime, state, threadId, step, correlationId);
            name = ctx.eval(raw, String.class);
        } else if (raw != null && (kind == LogSegmentKind.STEP || kind == LogSegmentKind.ITERATION)) {
            // Deferred names remain immutable templates; iterations/attempts evaluate their own names.
            name = raw;
        }

        if (name == null) {
            name = structuralName();
        }

        return SensitiveDataMasker.mask(name, runtime.getService(SensitiveDataHolder.class).get());
    }

    private String structuralName() {
        return switch (kind) {
            case ITERATION -> "iteration #" + (iterationIndex + 1);
            case ATTEMPT -> "attempt #" + (attemptIndex + 1);
            case ERROR -> "error";
            case PARALLEL -> "parallel";
            case STEP -> stepName(step);
        };
    }

    private static String stepName(Step step) {
        if (step instanceof TaskCall t) {
            return "task: " + t.getName();
        } else if (step instanceof ScriptCall s) {
            return "script: " + s.getLanguageOrRef();
        } else if (step instanceof FlowCall c) {
            return c.getFlowName().contains("${") ? "call" : "call: " + c.getFlowName();
        } else if (step instanceof GroupOfSteps) {
            return "block";
        } else if (step instanceof Expression) {
            return "expression";
        }
        return step.getClass().getSimpleName();
    }

    private Level getLogLevel(State state, ThreadId threadId) {
        if (kind == LogSegmentKind.ERROR || kind == LogSegmentKind.PARALLEL || !(step instanceof AbstractStep<?> s)) {
            LogContext parent = LogSegmentUtils.getLogContext(threadId, state);
            return parent != null ? parent.logLevel() : Level.INFO;
        }
        return SegmentedLogger.getLogLevel(s);
    }
}
