package com.walmartlabs.concord.runtime.v2.runner.compiler;

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

import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.runner.vm.*;
import com.walmartlabs.concord.svm.Command;

import javax.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Named
public class FlowCallCompiler implements StepCompiler<FlowCall> {

    @Override
    public boolean accepts(Step step) {
        return step instanceof FlowCall;
    }

    @Override
    public Command compile(CompilerContext context, FlowCall step) {
        UUID correlationId = UUID.randomUUID();
        FlowCallOptions options = Objects.requireNonNull(step.getOptions());

        Map<String, Serializable> outMapping = options.outExpr() != null && !options.outExpr().isEmpty() ? options.outExpr() : options.outMapping();

        WithItems withItems = options.withItems();
        if (withItems != null) {
            return StepSegments.withItems(new FlowCallCommand(correlationId, step), step, correlationId,
                    withItems, options.retry(), options.out(), outMapping,
                    options.errorSteps().isEmpty() ? null : CompilerUtils.compile(context, options.errorSteps()));
        }

        Command cmd = new FlowCallCommand(correlationId, step);

        Retry retry = options.retry();
        RetryWrapper retryCommand = null;
        if (retry != null) {
            retryCommand = new RetryWrapper(cmd, retry, step, correlationId);
            cmd = retryCommand;
        }

        Loop loop = options.loop();
        if (loop != null) {
            cmd = LoopWrapper.of(context, cmd, loop, options.out(), outMapping, options.outExpression(), step,
                    retryCommand != null ? LoopIterationCommand.retry(retryCommand) : StepSegments.iteration(cmd, step, correlationId));
        }

        List<Step> errorSteps = options.errorSteps();

        // an unnamed call without loop, retry or error has no segment
        if (SegmentedLogger.getSegmentName(step) == null && loop == null && retry == null && errorSteps.isEmpty()) {
            return cmd;
        }

        return StepSegments.step(cmd, step, correlationId, loop != null || retry != null,
                errorSteps.isEmpty() ? null : CompilerUtils.compile(context, errorSteps));
    }

}
