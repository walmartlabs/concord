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

import com.walmartlabs.concord.runtime.v2.model.GroupOfSteps;
import com.walmartlabs.concord.runtime.v2.model.GroupOptions;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.model.WithItems;
import com.walmartlabs.concord.runtime.v2.runner.vm.BlockCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.ErrorWrapper;
import com.walmartlabs.concord.runtime.v2.runner.vm.WithItemsWrapper;
import com.walmartlabs.concord.svm.Command;

import javax.inject.Named;
import java.util.List;
import java.util.stream.Collectors;

@Named
public final class GroupOfStepsCompiler implements StepCompiler<GroupOfSteps> {

    @Override
    public boolean accepts(Step step) {
        return step instanceof GroupOfSteps;
    }

    @Override
    public Command compile(CompilerContext context, GroupOfSteps step) {
        GroupOptions options = step.getOptions();

        // steps in the group
        Command cmd = compile(context, step.getSteps());

        // steps in the optional "error" block
        List<Step> errorSteps = options != null ? options.errorSteps() : null;
        if (errorSteps != null) {
            cmd = new ErrorWrapper(cmd, compile(context, errorSteps));
        }

        // add "withItems" if needed
        WithItems withItems = options != null ? options.withItems() : null;
        if (withItems != null) {
            return new WithItemsWrapper(cmd, withItems);
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
