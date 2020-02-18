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

import com.walmartlabs.concord.runtime.v2.model.FormCall;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.vm.FormCallCommand;
import com.walmartlabs.concord.svm.Command;

import javax.inject.Named;

@Named
public class FormCallCompiler implements StepCompiler<FormCall> {

    @Override
    public boolean accepts(Step step) {
        return step instanceof FormCall;
    }

    @Override
    public Command compile(CompilerContext context, FormCall step) {
        ProcessDefinition pd = context.processDefinition();
        return new FormCallCommand(step, pd.forms());
    }
}
