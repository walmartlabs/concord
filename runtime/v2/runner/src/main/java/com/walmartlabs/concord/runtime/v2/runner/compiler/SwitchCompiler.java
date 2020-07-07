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

import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.model.SwitchStep;
import com.walmartlabs.concord.runtime.v2.runner.vm.BlockCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.SwitchCommand;
import com.walmartlabs.concord.svm.Command;

import javax.inject.Named;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Named
public class SwitchCompiler implements StepCompiler<SwitchStep> {

    @Override
    public boolean accepts(Step step) {
        return step instanceof SwitchStep;
    }

    @Override
    public Command compile(CompilerContext context, SwitchStep step) {
        List<Map.Entry<String, Command>> caseCommands = new ArrayList<>();
        for (Map.Entry<String, List<Step>> kv : step.getCaseSteps()) {
            caseCommands.add(new AbstractMap.SimpleEntry<>(kv.getKey(), compile(context, kv.getValue())));
        }
        Command defaultCommand = compile(context, step.getDefaultSteps());
        return new SwitchCommand(step, caseCommands, defaultCommand);
    }

    private static Command compile(CompilerContext context, List<Step> steps) {
        if (steps == null) {
            return null;
        }

        return new BlockCommand(steps.stream()
                .map(s -> context.compiler().compile(context.processDefinition(), s))
                .collect(Collectors.toList()));
    }
}
