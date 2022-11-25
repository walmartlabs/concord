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

import com.oracle.truffle.js.scriptengine.GraalJSEngineFactory;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.sdk.Constants;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.script.*;
import java.io.BufferedWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DefaultScriptEvaluator implements ScriptEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DefaultScriptEvaluator.class);

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
    public ScriptResult eval(Context context, String language, Reader input, Map<String, Object> variables) {
        ScriptEngine engine = getEngine(language);

        if (engine == null) {
            throw new RuntimeException("Script engine not found: " + language);
        }

        Bindings b = ScriptEngineBindings.create(engine, language);

        ScriptResult scriptResult = new ScriptResult();
        ScriptContext ctx = new ScriptContext(context);
        for (String ctxVar : CONTEXT_VARIABLE_NAMES) {
            b.put(ctxVar, ctx);
        }
        b.put("tasks", new TaskAccessor(taskProviders, ctx));
        b.put("log", log);
        b.putAll(context.variables().toMap());
        b.putAll(variables);
        b.put("result", scriptResult);

        try {
            engine.eval(input, b);
            return scriptResult;
        } catch (ScriptException e) {
            if (e.getCause() instanceof PolyglotException) {
                throw new RuntimeException(e.getCause().getMessage());
            }
            throw new RuntimeException(e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getLanguage(String languageOrExtension) {
        for (ScriptEngineFactory factory : scriptEngineManager.getEngineFactories()) {
            try {
                if (listOrEmpty(factory.getNames()).contains(languageOrExtension)) {
                    return factory.getLanguageName();
                }
            } catch (Exception exp) {
                // ignore
            }

            try {
                if (listOrEmpty(factory.getExtensions()).contains(languageOrExtension)) {
                    return factory.getLanguageName();
                }
            } catch (Exception exp) {
                // ignore
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ScriptEngine getEngine(String language) {
        ScriptEngine engine;
        if (new GraalJSEngineFactory().getNames().contains(language)) {
            // Javascript array is converted in Java to an empty map #214 (https://github.com/oracle/graaljs/issues/214)
            HostAccess access = HostAccess.newBuilder(HostAccess.ALL)
                    .targetTypeMapping(Value.class, Object.class, Value::hasArrayElements, v -> new LinkedList<>(v.as(List.class))).build();

            engine = GraalJSScriptEngine.create(Engine.newBuilder()
                            .allowExperimentalOptions(true)
                            .option("engine.WarnInterpreterOnly", "false")
                            .option("js.nashorn-compat", "true")
                            .build(),
                    org.graalvm.polyglot.Context.newBuilder("js")
                            .allowHostAccess(access));
        } else {
            ScriptEngineProperties.applyFor(language);

            engine = scriptEngineManager.getEngineByName(language);
        }

        if (engine != null) {
            engine.getContext().setWriter(new BufferedWriter(new LogWriter()));
        }

        return engine;
    }

    private static List<String> listOrEmpty(List<String> items) {
        if (items == null) {
            return Collections.emptyList();
        }
        return items;
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

    private static class LogWriter extends Writer {

        @Override
        public void write(char[] cbuf, int off, int len) {
            if (len == 0) {
                return;
            }

            int l = cbuf[len - 1] == '\n' ? len - 1 : len;
            log.info("{}", new String(cbuf, off, l));
        }

        @Override
        public void flush() {
            //do nothing
        }

        @Override
        public void close() {
            // do nothing
        }
    }
}
