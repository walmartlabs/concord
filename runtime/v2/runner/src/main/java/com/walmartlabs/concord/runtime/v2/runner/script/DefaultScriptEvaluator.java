package com.walmartlabs.concord.runtime.v2.runner.script;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.MetadataProcessor;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.sdk.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.script.*;
import java.io.Reader;
import java.util.List;
import java.util.Map;

public class DefaultScriptEvaluator implements ScriptEvaluator {

    private static final Logger log = LoggerFactory.getLogger(MetadataProcessor.class);

    // TODO: deprecate "execution"? what about scripts - can't use "context" there?
    private static final String[] CONTEXT_VARIABLE_NAMES = {Constants.Context.CONTEXT_KEY, "execution"};

    private final TaskProviders taskProviders;
    private final ScriptEngineManager scriptEngineManager;

    @Inject
    public DefaultScriptEvaluator(TaskProviders taskProviders) {
        this.taskProviders = taskProviders;
        this.scriptEngineManager = new ScriptEngineManager();
    }

    @Override
    public void eval(Context context, String language, Reader input, Map<String, Object> variables) {
        ScriptEngine engine = getEngine(language);
        if (engine == null) {
            throw new RuntimeException("Script engine not found: " + language);
        }

        // expose all available variables plus the context
        ScriptContext ctx = new ScriptContext(context);
        Bindings b = engine.createBindings();
        for (String ctxVar: CONTEXT_VARIABLE_NAMES) {
            b.put(ctxVar, ctx);
        }
        b.put("tasks", new TaskAccessor(taskProviders, ctx));
        b.put("log", log);
        b.putAll(variables);

        try {
            engine.eval(input, b);
        } catch (ScriptException e) {
            throw new RuntimeException(e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasLanguage(String language) {
        for (ScriptEngineFactory factory : scriptEngineManager.getEngineFactories()) {
            List<String> names = null;
            try {
                names = factory.getNames();
            } catch (Exception exp) {
                // ignore
            }
            if (names != null && names.contains(language)) {
                return true;
            }
        }
        return false;
    }

    private ScriptEngine getEngine(String language) {
        return scriptEngineManager.getEngineByName(language);
    }

    public static class TaskAccessor {

        private final TaskProviders tasks;
        private final Context context;

        public TaskAccessor(TaskProviders tasks, Context context) {
            this.tasks = tasks;
            this.context = context;
        }

        public Object get(String key) {
            return tasks.createTask(context, key);
        }
    }
}
