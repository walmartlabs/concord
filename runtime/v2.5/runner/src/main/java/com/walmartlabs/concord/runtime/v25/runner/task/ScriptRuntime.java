package com.walmartlabs.concord.runtime.v25.runner.task;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v25.model.Values;
import com.walmartlabs.concord.runtime.v25.model.Definition25;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.plan.ExecutionPlan;
import com.walmartlabs.concord.runtime.v25.runner.plan.Instruction;
import com.walmartlabs.concord.runtime.v25.runner.scope.Scope;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;

final class ScriptRuntime {

    private static final Logger log = LoggerFactory.getLogger(ScriptRuntime.class);
    private static final Set<Integer> SUPPORTED_ES_VERSIONS = Set.of(3, 5, 6, 7, 2015, 2016, 2017, 2018,
            2019, 2020, 2021, 2022, 2024);

    private final TaskRegistry tasks;
    private final TaskEnvironment environment;
    private final ScriptEngineManager engines;
    private final GraalEngineFactory graalEngines;
    private final Consumer<Engine> closedPolyglotEngines;

    ScriptRuntime(TaskRegistry tasks, TaskEnvironment environment) {
        this(tasks, environment, new ScriptEngineManager());
    }

    ScriptRuntime(TaskRegistry tasks, TaskEnvironment environment, ScriptEngineManager engines) {
        this(tasks, environment, engines, GraalJSScriptEngine::create);
    }

    ScriptRuntime(TaskRegistry tasks, TaskEnvironment environment, ScriptEngineManager engines,
                  GraalEngineFactory graalEngines) {
        this(tasks, environment, engines, graalEngines, ignored -> {
        });
    }

    ScriptRuntime(TaskRegistry tasks, TaskEnvironment environment, ScriptEngineManager engines,
                  GraalEngineFactory graalEngines, Consumer<Engine> closedPolyglotEngines) {
        this.tasks = tasks;
        this.environment = environment;
        this.engines = engines;
        this.graalEngines = graalEngines;
        this.closedPolyglotEngines = closedPolyglotEngines;
    }

    TaskRuntime.Outcome execute(ExpressionService expressions, ExecutionPlan plan, Instruction instruction,
                                Object rawOverrides, Scope scope,
                                com.walmartlabs.concord.runtime.v2.sdk.NestedFlowExecutor nestedFlowExecutor,
                                TaskRuntime.StepContext stepContext) {
        assertDryRunReady(expressions, instruction, scope);
        var input = new LinkedHashMap<>(input(expressions.evaluate(instruction.options().get("in"), scope)));
        var overrides = input(expressions.evaluate(rawOverrides, scope));
        var merged = Definition25.deepMerge(input, overrides);
        input.clear();
        input.putAll(merged);
        var immutableInput = Values.map(input);
        var languageOrReference = expressions.evaluate(instruction.value(), scope, String.class);
        var body = instruction.options().get("body");
        var language = body != null ? language(languageOrReference) : language(extension(languageOrReference));
        try (var reader = body != null ? new StringReader(String.valueOf(body)) : resolve(languageOrReference)) {
            var suspension = new AtomicReference<com.walmartlabs.concord.runtime.v25.runner.engine.Suspension>();
            var context = new SdkContext(expressions, scope, plan, instruction, "script", stepContext, environment,
                    nestedFlowExecutor, request -> {
                        if (!suspension.compareAndSet(null, request)) {
                            throw new IllegalStateException("Script requested more than one suspension");
                        }
                    });
            var result = InvocationExecutor.callCurrent(() -> evaluate(context, language, reader, immutableInput));
            return new TaskRuntime.Outcome(result, null, suspension.get());
        } catch (IOException e) {
            throw new RuntimeException("Error reading script '" + languageOrReference + "'", e);
        }
    }

    Map<String, Object> evaluate(Context context, String language, Reader source,
                                 Map<String, Object> input) throws Exception {
        try (var lease = engine(language, input)) {
            var engine = lease.engine();
            engine.getContext().setWriter(new BufferedWriter(new LogWriter()));
            var bindings = engine.createBindings();
            var result = new Outputs();
            var scriptContext = new ScriptContext(context);
            bindings.put("context", scriptContext);
            bindings.put("execution", scriptContext);
            bindings.put("tasks", new Tasks(scriptContext));
            bindings.put("log", log);
            bindings.put("isDryRun", environment.dryRun());
            bindings.putAll(scriptContext.variables().toMap());
            bindings.putAll(input);
            bindings.put("result", result);
            try {
                engine.eval(source, bindings);
                return result(bindings.get("result"));
            } catch (ScriptException e) {
                var cause = e.getCause();
                throw new UserDefinedException(cause != null && cause.getMessage() != null
                        ? cause.getMessage()
                        : e.getMessage());
            }
        }
    }

    private static Map<String, Object> result(Object value) {
        if (value instanceof Outputs outputs) {
            return outputs.items();
        }
        value = Outputs.sanitize(value);
        if (!(value instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("Script result must be a mapping, got: "
                    + (value == null ? "null" : value.getClass().getName()));
        }
        var result = new LinkedHashMap<String, Object>();
        values.forEach((key, item) -> result.put(String.valueOf(key), Outputs.sanitize(item)));
        return Values.map(result);
    }

    private EngineLease engine(String language, Map<String, Object> input) {
        ScriptEngine result;
        Engine polyglotEngine = null;
        var graalFactory = new GraalJSEngineFactory();
        if (graalFactory.getNames().contains(language)) {
            var hostAccess = HostAccess.newBuilder(HostAccess.ALL)
                    .targetTypeMapping(Value.class, Object.class, Value::hasArrayElements,
                            value -> value.as(List.class))
                    .build();
            var context = org.graalvm.polyglot.Context.newBuilder("js")
                    .allowHostAccess(hostAccess)
                    .option("js.ecmascript-version", esVersion(input));
            polyglotEngine = Engine.newBuilder()
                    .allowExperimentalOptions(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .option("js.nashorn-compat", "true")
                    .build();
            result = graalEngines.create(polyglotEngine, context);
        } else {
            result = engines.getEngineByName(language);
        }
        if (result == null) {
            if (polyglotEngine != null) {
                polyglotEngine.close();
            }
            throw new UserDefinedException("Unknown language '" + language + "'. Check process dependencies.");
        }
        // Both factories are invoked for every script evaluation, so this invocation owns the engine.
        return EngineLease.owned(result, polyglotEngine, closedPolyglotEngines);
    }

    private String language(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Can't determine the script language");
        }
        for (var factory : engines.getEngineFactories()) {
            if (factory.getNames().contains(name) || factory.getExtensions().contains(name)) {
                return factory.getNames().contains(name) ? name : factory.getNames().getFirst();
            }
        }
        if (new GraalJSEngineFactory().getNames().contains(name)) {
            return name;
        }
        throw new UserDefinedException("Unknown language '" + name + "'. Check process dependencies.");
    }

    private Reader resolve(String reference) throws IOException {
        var configured = environment.services().get(ResourceResolver.class);
        if (configured instanceof ResourceResolver resolver) {
            var result = resolver.resolve(reference);
            if (result == null) {
                throw new IllegalArgumentException("Resource not found: " + reference);
            }
            return result;
        }
        var root = environment.workingDirectory().toAbsolutePath().normalize();
        var path = root.resolve(reference).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Script resource must stay under the process working directory: "
                    + reference);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Resource not found: " + reference);
        }
        return Files.newBufferedReader(path);
    }

    private void assertDryRunReady(ExpressionService expressions, Instruction instruction, Scope scope) {
        if (!environment.dryRun()) {
            return;
        }
        var meta = instruction.options().get("meta");
        var ready = meta instanceof Map<?, ?> values ? values.get("dryRunReady") : null;
        if (!Boolean.TRUE.equals(expressions.evaluate(ready, scope))) {
            throw new UserDefinedException("Dry-run mode is not supported for this 'script' step");
        }
    }

    private static Map<String, Object> input(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("Script 'in' must evaluate to a mapping, got: "
                    + value.getClass().getName());
        }
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Values.map(result);
    }

    private static String extension(String reference) {
        if (reference == null) {
            return null;
        }
        var index = reference.lastIndexOf('.');
        return index >= 0 && index + 1 < reference.length() ? reference.substring(index + 1) : null;
    }

    private static String esVersion(Map<String, Object> input) {
        var value = input.getOrDefault("esVersion", 6);
        if (!(value instanceof Number number) || !SUPPORTED_ES_VERSIONS.contains(number.intValue())) {
            throw new UserDefinedException("unsupported esVersion: " + value);
        }
        return Integer.toString(number.intValue());
    }

    @FunctionalInterface
    interface GraalEngineFactory {

        ScriptEngine create(Engine engine, org.graalvm.polyglot.Context.Builder context);
    }

    private record EngineLease(ScriptEngine engine, Engine polyglotEngine, Consumer<Engine> closedPolyglotEngines,
                               boolean owned) implements AutoCloseable {

        private static EngineLease owned(ScriptEngine engine, Engine polyglotEngine,
                                         Consumer<Engine> closedPolyglotEngines) {
            return new EngineLease(engine, polyglotEngine, closedPolyglotEngines, true);
        }

        @Override
        public void close() throws Exception {
            try {
                if (owned && engine instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } finally {
                if (polyglotEngine != null) {
                    polyglotEngine.close();
                    closedPolyglotEngines.accept(polyglotEngine);
                }
            }
        }
    }

    interface ResourceResolver {
        Reader resolve(String reference) throws IOException;
    }


    public final class Tasks {
        private final Context context;

        private Tasks(Context context) {
            this.context = context;
        }

        public Task get(String name) {
            return tasks.create(context, name);
        }
    }

    public static final class Outputs {
        private final Map<String, Object> items = new LinkedHashMap<>();

        public Outputs set(String name, Object value) {
            items.put(name, sanitize(value));
            return this;
        }

        private Map<String, Object> items() {
            return Values.map(items);
        }

        static Object sanitize(Object value) {
            if (value instanceof Map<?, ?> values) {
                var result = new LinkedHashMap<Object, Object>();
                values.forEach((key, item) -> result.put(sanitize(key), sanitize(item)));
                return result;
            }
            if (value instanceof Collection<?> values) {
                var result = new ArrayList<Object>(values.size());
                values.forEach(item -> result.add(sanitize(item)));
                return result;
            }
            if (value != null && value.getClass().isArray()) {
                var result = new ArrayList<Object>(Array.getLength(value));
                for (var i = 0; i < Array.getLength(value); i++) {
                    result.add(sanitize(Array.get(value, i)));
                }
                return result;
            }
            if (value instanceof Double number && Double.isFinite(number) && number == Math.rint(number)) {
                var integral = number.longValue();
                return integral >= Integer.MIN_VALUE && integral <= Integer.MAX_VALUE ? (int) integral : integral;
            }
            if (!(value instanceof Value polyglot)) {
                return value;
            }
            if (polyglot.isNull()) {
                return null;
            }
            if (polyglot.isBoolean()) {
                return polyglot.asBoolean();
            }
            if (polyglot.fitsInInt()) {
                return polyglot.asInt();
            }
            if (polyglot.fitsInLong()) {
                return polyglot.asLong();
            }
            if (polyglot.fitsInDouble()) {
                return polyglot.asDouble();
            }
            if (polyglot.isString()) {
                return polyglot.asString();
            }
            if (polyglot.hasArrayElements()) {
                var result = new ArrayList<Object>((int) polyglot.getArraySize());
                for (long i = 0; i < polyglot.getArraySize(); i++) {
                    result.add(sanitize(polyglot.getArrayElement(i)));
                }
                return result;
            }
            if (polyglot.hasMembers()) {
                var result = new LinkedHashMap<String, Object>();
                polyglot.getMemberKeys().forEach(key -> result.put(key, sanitize(polyglot.getMember(key))));
                return result;
            }
            if (polyglot.isHostObject()) {
                return polyglot.asHostObject();
            }
            return polyglot.toString();
        }
    }

    private static final class LogWriter extends Writer {
        @Override
        public void write(char[] buffer, int offset, int length) {
            if (length == 0) {
                return;
            }
            var actualLength = buffer[offset + length - 1] == '\n' ? length - 1 : length;
            if (actualLength > 0) {
                log.info("{}", new String(buffer, offset, actualLength));
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
