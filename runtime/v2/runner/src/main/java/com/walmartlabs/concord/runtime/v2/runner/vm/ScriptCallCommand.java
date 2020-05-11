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

import com.walmartlabs.concord.runtime.v2.model.ScriptCall;
import com.walmartlabs.concord.runtime.v2.model.ScriptCallOptions;
import com.walmartlabs.concord.runtime.v2.runner.ResourceResolver;
import com.walmartlabs.concord.runtime.v2.runner.ScriptEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.*;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Calls the specified script task. Responsible for preparing the script's input.
 */
public class ScriptCallCommand extends StepCommand<ScriptCall> {

    private static final long serialVersionUID = 1L;

    public ScriptCallCommand(ScriptCall step) {
        super(step);
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        Context ctx = contextFactory.create(runtime, state, threadId, getStep(), UUID.randomUUID());

        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
        ScriptEvaluator scriptEvaluator = runtime.getService(ScriptEvaluator.class);
        ResourceResolver resourceResolver = runtime.getService(ResourceResolver.class);

        ScriptCall call = getStep();
        ScriptCallOptions opts = call.getOptions();
        Map<String, Object> input = VMUtils.prepareInput(expressionEvaluator, ctx, opts.input());

        String language = getLanguage(call);
        Reader content = getContent(expressionEvaluator, resourceResolver, ctx, call); // TODO close reader?

        ThreadLocalContext.withContext(ctx, () ->
                scriptEvaluator.eval(ctx, language, content, input));
    }

    private static String getLanguage(ScriptCall call) {
        if (call.getOptions().body() != null) {
            return call.getName();
        }
        return getExtension(call.getName());
    }

    private static String getExtension(String s) {
        if (s == null) {
            return null;
        }

        int i = s.lastIndexOf(".");
        if (i < 0 || i + 1 >= s.length()) {
            return null;
        }

        return s.substring(i + 1);
    }

    private static Reader getContent(ExpressionEvaluator expressionEvaluator, ResourceResolver resourceResolver, Context ctx, ScriptCall call) {
        if (call.getOptions().body() != null) {
            return new StringReader(Objects.requireNonNull(call.getOptions().body()));
        }

        String ref = expressionEvaluator.eval(EvalContextFactory.global(ctx), call.getName(), String.class);
        try {
            InputStream in = resourceResolver.resolve(ref);
            if (in == null) {
                throw new RuntimeException("Resource not found: " + ref);
            }
            return new InputStreamReader(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
