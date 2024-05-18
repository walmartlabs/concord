package com.walmartlabs.concord.runtime.v2.runner.vm;

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

import com.walmartlabs.concord.runtime.v2.model.Checkpoint;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.ProcessSnapshot;
import com.walmartlabs.concord.runtime.v2.runner.SynchronizationService;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.svm.Command;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

public class CheckpointCommand extends StepCommand<Checkpoint> {

    private static final long serialVersionUID = 1L;

    public CheckpointCommand(Checkpoint step) {
        super(step);
    }

    @Override
    public Command copy() {
        return new CheckpointCommand(getStep());
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        // eval the name in case it contains an expression
        Context ctx = runtime.getService(Context.class);
        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        String name = ee.eval(ecf.global(ctx), getStep().getName(), String.class);

        runtime.getService(SynchronizationService.class).point(() -> {
            CheckpointService checkpointService = runtime.getService(CheckpointService.class);
            ProcessDefinition processDefinition = runtime.getService(ProcessDefinition.class);

            // cleanup the internal state to reduce the serialized data size
            state.gc();

            // TODO validate checkpoint name

            checkpointService.create(threadId, getCorrelationId(), name, runtime, ProcessSnapshot.builder()
                    .vmState(state)
                    .processDefinition(processDefinition)
                    .build());
        });
    }
}
