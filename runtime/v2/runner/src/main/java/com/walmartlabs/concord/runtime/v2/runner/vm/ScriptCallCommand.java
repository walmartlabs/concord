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
import com.walmartlabs.concord.runtime.v2.runner.script.ScriptEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.script.ScriptResult;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Calls the specified script task. Responsible for preparing the script's input.
 */
public class ScriptCallCommand extends StepCommand<ScriptCall> implements ElementEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ScriptCallCommand.class);

    private static final long serialVersionUID = 1L;

    public ScriptCallCommand(UUID correlationId, ScriptCall step) {
        super(correlationId, step);
    }

    @Override
    public String getDescription(State state, ThreadId threadId) {
        return "Script: " + getStep().getLanguageOrRef();
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        Context ctx = runtime.getService(Context.class);

        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
        ScriptEvaluator scriptEvaluator = runtime.getService(ScriptEvaluator.class);
        ResourceResolver resourceResolver = runtime.getService(ResourceResolver.class);

        ScriptCall call = getStep();
        ScriptCallOptions opts = Objects.requireNonNull(call.getOptions());

        assertScriptDryRunReady(ctx, opts);

        Map<String, Object> input = VMUtils.prepareInput(ecf, expressionEvaluator, ctx, opts.input(), opts.inputExpression());

        String language = getLanguage(ecf, expressionEvaluator, scriptEvaluator, ctx, call);
        Reader content = getContent(ecf, expressionEvaluator, resourceResolver, ctx, call);

        ScriptResult scriptResult;
        try {
            scriptResult = scriptEvaluator.eval(ctx, language, content, input);
        } finally {
            try {
                content.close();
            } catch (IOException e) {
                // we don't have to do anything about it, but we're going to log the error just in case
                log.warn("Error while closing the script's reader: {}", e.getMessage() + ". This is most likely a bug.");
            }
        }

        OutputUtils.process(runtime, ctx, scriptResult.items(), opts.out(), opts.outExpr());
    }

    private static String getLanguage(EvalContextFactory ecf, ExpressionEvaluator expressionEvaluator, ScriptEvaluator scriptEvaluator, Context ctx, ScriptCall call) {
        String languageOrRef = expressionEvaluator.eval(ecf.global(ctx), call.getLanguageOrRef(), String.class);

        // if we have body then languageOrRef is language
        if (Objects.requireNonNull(call.getOptions()).body() != null) {
            return assertLanguage(scriptEvaluator, languageOrRef);
        }

        String maybeLanguage = getExtension(languageOrRef);
        if (maybeLanguage != null) {
            return assertLanguage(scriptEvaluator, maybeLanguage);
        }

        if (scriptEvaluator.getLanguage(languageOrRef) != null) {
            throw new RuntimeException("Invalid step definition: 'body' parameter not found.");
        }

        throw new RuntimeException("Can't determine the script language: " + call.getLanguageOrRef() + " (" + languageOrRef + "). " +
                "Check if the script's name is correct and the required dependencies are declared.");
    }

    private static String assertLanguage(ScriptEvaluator scriptEvaluator, String language) {
        String normalizedLanguage = scriptEvaluator.getLanguage(language);

        if (normalizedLanguage != null) {
            return normalizedLanguage;
        }

        throw new UserDefinedException("Unknown language '" + language + "'. Check process dependencies.");
    }

    private static String getExtension(String s) {
        int i = s.lastIndexOf(".");
        if (i < 0 || i + 1 >= s.length()) {
            return null;
        }

        return s.substring(i + 1);
    }

    private static Reader getContent(EvalContextFactory ecf, ExpressionEvaluator expressionEvaluator, ResourceResolver resourceResolver, Context ctx, ScriptCall call) {
        if (call.getOptions().body() != null) {
            return new StringReader(Objects.requireNonNull(call.getOptions().body()));
        }

        String ref = expressionEvaluator.eval(ecf.global(ctx), call.getLanguageOrRef(), String.class);
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

    private static void assertScriptDryRunReady(Context ctx, ScriptCallOptions opts) {
        if (!ctx.processConfiguration().dryRun()) {
            return;
        }

        if (StepOptionsUtils.isDryRunReady(ctx, opts)) {
            return;
        }

        throw new IllegalStateException("Dry-run mode is not supported for this 'script' step");
    }
}
