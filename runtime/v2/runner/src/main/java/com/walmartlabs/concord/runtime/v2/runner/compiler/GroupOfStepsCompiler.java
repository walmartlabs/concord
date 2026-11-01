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
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentKind;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.runner.vm.BlockCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.LoopWrapper;
import com.walmartlabs.concord.runtime.v2.runner.vm.WithItemsWrapper;
import com.walmartlabs.concord.svm.Command;

import javax.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Named
public final class GroupOfStepsCompiler implements StepCompiler<GroupOfSteps> {

    @Override
    public boolean accepts(Step step) {
        return step instanceof GroupOfSteps;
    }

    @Override
    public Command compile(CompilerContext context, GroupOfSteps step) {
        UUID correlationId = UUID.randomUUID();
        Command cmd = compile(context, step.getSteps());

        GroupOfStepsOptions options = Objects.requireNonNull(step.getOptions());
        WithItems withItems = options.withItems();
        if (withItems != null) {
            return WithItemsWrapper.of(cmd, withItems, options.out(), Collections.emptyMap(), step);
        }

        Loop loop = options.loop();
        if (loop != null) {
            cmd = LoopWrapper.of(context, cmd, loop, options.out(), Collections.emptyMap(), null, step,
                    StepSegments.iteration(cmd, step, correlationId));
        }

        List<Step> errorSteps = options.errorSteps();

        // an unnamed group without loop or error has no segment
        if (SegmentedLogger.getSegmentName(step) == null && loop == null && errorSteps.isEmpty()) {
            return cmd;
        }

        return StepSegments.step(cmd, step, correlationId, loop != null ? LogSegmentKind.LOOP : LogSegmentKind.STEP,
                errorSteps.isEmpty() ? null : compile(context, errorSteps));
    }

    private static BlockCommand compile(CompilerContext context, List<Step> steps) {
        if (steps == null) {
            return null;
        }

        return new BlockCommand(steps.stream()
                .map(s -> context.compiler().compile(context.processDefinition(), s))
                .collect(Collectors.toList()));
    }
}
