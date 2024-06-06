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
import com.walmartlabs.concord.runtime.v2.runner.vm.BlockCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.ErrorWrapper;
import com.walmartlabs.concord.runtime.v2.runner.vm.LoopWrapper;
import com.walmartlabs.concord.runtime.v2.runner.vm.WithItemsWrapper;
import com.walmartlabs.concord.svm.Command;

import javax.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Named
public final class GroupOfStepsCompiler implements StepCompiler<GroupOfSteps> {

    @Override
    public boolean accepts(Step step) {
        return step instanceof GroupOfSteps;
    }

    @Override
    public Command compile(CompilerContext context, GroupOfSteps step) {
        Command cmd = compile(context, step.getSteps());

        GroupOfStepsOptions options = Objects.requireNonNull(step.getOptions());
        WithItems withItems = options.withItems();
        if (withItems != null) {
            return WithItemsWrapper.of(cmd, withItems, options.out(), Collections.emptyMap());
        }

        Loop loop = options.loop();
        if (loop != null) {
            cmd = LoopWrapper.of(context, cmd, loop, options.out(), Collections.emptyMap(), step);
        }

        List<Step> errorSteps = options.errorSteps();
        if (!options.errorSteps().isEmpty()) {
            cmd = new ErrorWrapper(cmd, compile(context, errorSteps));
        }

        return cmd;
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
