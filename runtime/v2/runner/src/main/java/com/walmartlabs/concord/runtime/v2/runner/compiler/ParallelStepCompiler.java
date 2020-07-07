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

import com.walmartlabs.concord.runtime.v2.model.ParallelBlock;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.vm.ParallelCommand;
import com.walmartlabs.concord.svm.Command;

import javax.inject.Named;
import java.util.List;
import java.util.stream.Collectors;

@Named
public class ParallelStepCompiler implements StepCompiler<ParallelBlock> {

    @Override
    public boolean accepts(Step step) {
        return step instanceof ParallelBlock;
    }

    @Override
    public Command compile(CompilerContext context, ParallelBlock step) {
        List<Command> steps = step.getSteps().stream()
                .map(s -> context.compiler().compile(context.processDefinition(), s))
                .collect(Collectors.toList());

        return new ParallelCommand(step, steps);
    }
}
