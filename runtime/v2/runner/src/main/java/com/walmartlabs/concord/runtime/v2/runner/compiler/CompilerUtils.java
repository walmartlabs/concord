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

import com.walmartlabs.concord.runtime.v2.model.Flow;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Profile;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.vm.BlockCommand;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.svm.Command;

import java.util.List;
import java.util.stream.Collectors;

public final class CompilerUtils {

    public static Command compile(Compiler compiler, ProcessConfiguration processConfiguration, ProcessDefinition pd, String flowName) {
        Flow flow = pd.flows().get(flowName);
        for (String activeProfile : processConfiguration.processInfo().activeProfiles()) {
            Flow maybeFlow = pd.profiles().getOrDefault(activeProfile, Profile.builder().build()).flows().get(flowName);
            if (maybeFlow != null) {
                flow = maybeFlow;
            }
        }
        if (flow == null) {
            throw new IllegalArgumentException("Flow not found: " + flowName);
        }

        List<Command> commands = flow.steps().stream().map(s -> compiler.compile(pd, s)).collect(Collectors.toList());
        return new BlockCommand(commands);
    }

    public static Command compile(CompilerContext context, List<Step> steps) {
        return new BlockCommand(steps.stream()
                .map(s -> context.compiler().compile(context.processDefinition(), s))
                .collect(Collectors.toList()));
    }

    private CompilerUtils() {
    }
}
