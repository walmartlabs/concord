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
import com.walmartlabs.concord.runtime.v2.runner.vm.ErrorWrapper;
import com.walmartlabs.concord.runtime.v2.runner.vm.FlowCallCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.RetryWrapper;
import com.walmartlabs.concord.runtime.v2.runner.vm.WithItemsWrapper;
import com.walmartlabs.concord.svm.Command;

import javax.inject.Named;
import java.util.List;

@Named
public class FlowCallCompiler implements StepCompiler<FlowCall> {

    @Override
    public boolean accepts(Step step) {
        return step instanceof FlowCall;
    }

    @Override
    public Command compile(CompilerContext context, FlowCall step) {
        Command cmd = new FlowCallCommand(step);

        FlowCallOptions options = step.getOptions();

        Retry retry = options.retry();
        if (retry != null) {
            cmd = new RetryWrapper(cmd, retry);
        }

        WithItems withItems = options.withItems();
        if (withItems != null) {
            cmd = new WithItemsWrapper(cmd, withItems, options.out());
        }

        List<Step> errorSteps = options.errorSteps();
        if (!errorSteps.isEmpty()) {
            cmd = new ErrorWrapper(cmd, CompilerUtils.compile(context, errorSteps));
        }

        return cmd;
    }
}
