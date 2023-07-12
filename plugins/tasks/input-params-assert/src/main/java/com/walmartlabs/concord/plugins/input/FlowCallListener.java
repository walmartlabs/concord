package com.walmartlabs.concord.plugins.input;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.Location;
import com.walmartlabs.concord.runtime.v2.runner.vm.FlowCallCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.VMUtils;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class FlowCallListener implements ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(FlowCallListener.class);

    private final FlowCallInputParamsAssert inputParamsAssert;

    @Inject
    public FlowCallListener(FlowCallInputParamsAssert inputParamsAssert) {
        this.inputParamsAssert = inputParamsAssert;
    }

    @Override
    public Result afterCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
        if (cmd instanceof FlowCallCommand) {
            String flowName = VMUtils.getLocal(state, threadId, "flowName");
            try {
                inputParamsAssert.process(flowName, VMUtils.getCombinedLocals(state, threadId));
            } catch (UserDefinedException e) {
                log.error("{} {}", Location.toErrorPrefix(((FlowCallCommand) cmd).getStep().getLocation()), e.getMessage());
                throw e;
            }
        }

        return ExecutionListener.super.afterCommand(runtime, vm, state, threadId, cmd);
    }
}
