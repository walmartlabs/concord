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

import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Takes the input, interpolates its values and sets the result
 * to all root frame's local variables.
 * <p/>
 * Optionally takes a list of threads which root frames should be
 * updated with provided variables.
 */
public class UpdateLocalsCommand implements Command {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> input;
    private final Collection<ThreadId> threadIds;

    public UpdateLocalsCommand(Map<String, Object> input) {
        this(input, Collections.emptyList());
    }

    public UpdateLocalsCommand(Map<String, Object> input, Collection<ThreadId> threadIds) {
        this.input = input;
        this.threadIds = threadIds;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        // don't "pop" the stack, this command is a special case and evaluated separately

        // create the context explicitly as this command is evaluated outside or the regular
        // loop and doesn't inherit StepCommand
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        Context ctx = contextFactory.create(runtime, state, threadId, null);

        Collection<ThreadId> threads = threadIds;
        if (threads.isEmpty()) {
            threads = Collections.singletonList(threadId);
        }

        // allow access to arguments from arguments:
        /* e.g.
           configuration:
             arguments:
               args:
                 k1: v1
                 k2: ${context.variables().get('args.k1')}
         */
        for (ThreadId tid : threads) {
            Frame root = VMUtils.assertNearestRoot(state, tid);
            VMUtils.putLocals(root, input);
        }

        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        Map<String, Object> m = ee.evalAsMap(ecf.scope(ctx), input);

        for (ThreadId tid : threads) {
            List<Frame> frames = state.getFrames(tid);

            for (Frame f : frames) {
                if (f.getType() == FrameType.ROOT) {
                    VMUtils.putLocals(f, m);
                }
            }
        }
    }
}
