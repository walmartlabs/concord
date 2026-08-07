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

import com.google.inject.spi.Elements;
import com.walmartlabs.concord.plugins.mock.MockModule;
import com.walmartlabs.concord.plugins.mock.MockDefinitionProvider;
import com.walmartlabs.concord.plugins.mock.MockTask;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessInfo;
import com.walmartlabs.concord.runtime.v2.sdk.ProjectInfo;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveData;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.runtime.v25.model.parser.DefinitionParser;
import com.walmartlabs.concord.runtime.v25.runner.EngineFixture;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessResult;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessStatus;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.plan.PlanCompiler;
import org.junit.jupiter.api.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRuntimeTest {

    @Test
    void executesTaskOffSchedulerAndTaskMethodExpressions() throws Exception {
        var events = new ArrayList<String>();
        var sensitiveData = new SensitiveValues();
        var validator = new TaskRuntime.Validator() {
            @Override
            public void validateInput(String taskName, Map<String, Object> input) {
                events.add("validate-input:" + taskName);
            }

            @Override
            public void validateOutput(String taskName, Map<String, Object> output) {
                events.add("validate-output:" + taskName);
            }
        };
        var hook = new TaskRuntime.TaskHook() {
            @Override
            public void before(TaskRuntime.Invocation invocation) {
                events.add("before:" + invocation.methodName());
            }

            @Override
            public void after(TaskRuntime.Invocation invocation, Object result, Throwable failure) {
                events.add("after:" + invocation.methodName());
            }
        };
        var environment = environment(false, sensitiveData,
                Map.of("probe", Map.of("left", 2, "right", 1)));
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new Provider())), environment,
                validator, List.of(hook));
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    base: 5
                flows:
                  default:
                    - task: probe
                      meta:
                        compatibilityMarker: present
                      out: taskResult
                      in:
                        right: ${base + 2}
                    - expr: ${probe.add(taskResult.left, taskResult.right)}
                      out: sum
                    - expr: ${probe.echo('secret-value')}
                      out: echoed
                    - expr: ${sensitive('marked-secret')}
                      out: marked
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status());
        assertEquals(2, ((Map<?, ?>) result.variables().get("taskResult")).get("left"));
        assertEquals(7L, ((Map<?, ?>) result.variables().get("taskResult")).get("right"));
        assertEquals(5, ((Map<?, ?>) result.variables().get("taskResult")).get("visibleBase"));
        assertEquals(true, ((Map<?, ?>) result.variables().get("taskResult")).get("virtualThread"));
        assertEquals("probe", ((Map<?, ?>) result.variables().get("taskResult")).get("currentTask"));
        assertEquals("concord-v2.5",
                ((Map<?, ?>) result.variables().get("taskResult")).get("processDefinitionRuntime"));
        assertEquals(true, ((Map<?, ?>) result.variables().get("taskResult")).get("currentThreadPresent"));
        assertEquals("concord.yml", ((Map<?, ?>) result.variables().get("taskResult")).get("currentStepFile"));
        assertEquals("present", ((Map<?, ?>) result.variables().get("taskResult")).get("currentStepMeta"));
        assertEquals(9L, result.variables().get("sum"));
        assertEquals("secret-value", result.variables().get("echoed"));
        assertTrue(sensitiveData.get().contains("secret-value"));
        assertTrue(sensitiveData.get().contains("marked-secret"));
        assertEquals(List.of("validate-input:probe", "before:execute", "after:execute",
                "validate-output:probe", "before:add", "after:add", "before:echo", "after:echo"), events);
        assertEquals(1, runtime.history().size());
    }

    @Test
    void publishesFailedOutputWhenIgnoreErrorsContinues() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: fail
                      ignoreErrors: true
                      out: failedResult
                    - set:
                        continued: true
                """, runtime(false));

        assertEquals(ProcessStatus.SUCCEEDED, result.status());
        assertEquals(false, ((Map<?, ?>) result.variables().get("failedResult")).get("ok"));
        assertEquals("expected failure", ((Map<?, ?>) result.variables().get("failedResult")).get("error"));
        assertEquals(42, ((Map<?, ?>) result.variables().get("failedResult")).get("partial"));
        assertEquals(true, result.variables().get("continued"));
    }

    @Test
    void failsNonIgnoredTaskWithSourceLocation() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: fail
                      out: failedResult
                    - set:
                        unreachable: true
                """, runtime(false));

        assertEquals(ProcessStatus.FAILED, result.status());
        assertEquals("expected failure", result.failure().message());
        assertEquals("flows.default[0]", result.failure().path());
        assertFalse(result.variables().containsKey("unreachable"));
        assertFalse(result.variables().containsKey("failedResult"));
    }

    @Test
    void convertsTaskSuspensionIntoTerminalProcessState() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: suspend
                    - set:
                        unreachable: true
                """, runtime(false));

        assertEquals(ProcessStatus.SUSPENDED, result.status());
        assertNotNull(result.suspension());
        assertEquals("wait-for-test", result.suspension().eventName());
        assertEquals("flows.default[0]", result.suspension().path());
        assertFalse(result.variables().containsKey("unreachable"));
        assertNull(result.failure());
    }

    @Test
    void enforcesDryRunAnnotation() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: unsafe
                """, runtime(true));

        assertEquals(ProcessStatus.FAILED, result.status());
        assertEquals("Dry-run mode is not supported for 'unsafe' task", result.failure().message());

        var accepted = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    allowDryRun: true
                flows:
                  default:
                    - task: unsafe
                      meta:
                        dryRunReady: ${allowDryRun}
                """, runtime(true));
        assertEquals(ProcessStatus.SUCCEEDED, accepted.status(), String.valueOf(accepted.failure()));
    }

    @Test
    void enforcesDryRunReadinessForTaskMethodExpressions() throws Exception {
        var rejected = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: ${unsafe.message()}
                      out: message
                """, runtime(true));
        assertEquals(ProcessStatus.FAILED, rejected.status());
        assertEquals("Dry-run mode is not supported for 'unsafe' task", rejected.failure().message());

        var accepted = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: ${probe.add(3, 4)}
                      out: sum
                """, runtime(true));
        assertEquals(ProcessStatus.SUCCEEDED, accepted.status(), String.valueOf(accepted.failure()));
        assertEquals(7L, accepted.variables().get("sum"));
    }

    @Test
    void executesInlineJavascriptWithSdkBindingsAndExplicitOutputs() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - script: js
                      in:
                        base: 5
                      body: |
                        context.variables().set("sideEffect", "visible");
                        context.variables().set("structured", {a: 1, items: [2, 3]});
                        result.set("sum", base + 2);
                        result.set("taskValue", tasks.get("probe").add(3, 4));
                      out:
                        scriptSum: ${result.sum}
                        taskValue: ${result.taskValue}
                        structuredValue: ${structured.a}
                        structuredItems: ${structured.items}
                """, runtime(false));

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(7, result.variables().get("scriptSum"));
        assertEquals(7L, result.variables().get("taskValue"));
        assertEquals("visible", result.variables().get("sideEffect"));
        assertEquals(1, result.variables().get("structuredValue"));
        assertEquals(List.of(2, 3), result.variables().get("structuredItems"));
    }

    @Test
    void exposesNestedFlowExecutionToScripts() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - script: js
                      body: |
                        result.set("nested", execution.nestedFlowExecutor().execute("child", {value: "from-script"}));
                      out:
                        nested: ${result.nested}
                  child:
                    - set:
                        result: nested-result
                """, runtime(false));

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("nested-result", result.variables().get("nested"));
    }

    @Test
    void deepMergesRetryInputForTasksAndScripts() throws Exception {
        RetryInputTask.calls.set(0);
        var taskResult = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: retry-input
                      in:
                        config:
                          host: host
                          timeout: 1
                      retry:
                        times: 1
                        delay: 0
                        in:
                          config:
                            timeout: 2
                      out: taskResult
                """, new TaskRuntime(new TaskRegistry(List.of(new RetryInputProvider())),
                environment(false, new SensitiveValues(), Map.of())));

        assertEquals(ProcessStatus.SUCCEEDED, taskResult.status(), String.valueOf(taskResult.failure()));
        assertEquals(Map.of("host", "host", "timeout", 2),
                ((Map<?, ?>) taskResult.variables().get("taskResult")).get("config"));

        var scriptResult = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - script: js
                      in:
                        config:
                          host: host
                          timeout: 1
                      retry:
                        times: 1
                        delay: 0
                        in:
                          config:
                            timeout: 2
                      body: |
                        if (context.variables().get("__retry_attemptNo") == 0) {
                          throw new Error("retry");
                        }
                        result.set("config", config);
                      out: scriptResult
                """, runtime(false));

        assertEquals(ProcessStatus.SUCCEEDED, scriptResult.status(), String.valueOf(scriptResult.failure()));
        assertEquals(Map.of("host", "host", "timeout", 2),
                ((Map<?, ?>) scriptResult.variables().get("scriptResult")).get("config"));
    }

    @Test
    void closesOwnedScriptEngineAfterSuccessfulEvaluation() throws Exception {
        var engine = new RecordingScriptEngine(null, null);

        var result = scriptRuntime(engine).evaluate(scriptContext(), "close-test", new StringReader(""), Map.of());

        assertEquals(Map.of(), result);
        assertEquals(1, engine.closeCalls.get());
    }

    @Test
    void preservesScriptFailureWhenClosingOwnedEngineFails() {
        var evaluationFailure = new ScriptException("evaluation failed");
        var closeFailure = new IllegalStateException("close failed");
        var engine = new RecordingScriptEngine(evaluationFailure, closeFailure);

        var failure = assertThrows(UserDefinedException.class,
                () -> scriptRuntime(engine).evaluate(scriptContext(), "close-test", new StringReader(""), Map.of()));

        assertEquals("evaluation failed", failure.getMessage());
        assertEquals(1, engine.closeCalls.get());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(closeFailure, failure.getSuppressed()[0]);
    }

    @Test
    void reportsCloseFailureAfterSuccessfulScriptEvaluation() {
        var closeFailure = new IllegalStateException("close failed");
        var engine = new RecordingScriptEngine(null, closeFailure);

        var failure = assertThrows(IllegalStateException.class,
                () -> scriptRuntime(engine).evaluate(scriptContext(), "close-test", new StringReader(""), Map.of()));

        assertSame(closeFailure, failure);
        assertEquals(1, engine.closeCalls.get());
    }

    @Test
    void honorsReassignedScriptResultBinding() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - script: js
                      body: |
                        result = {value: 7};
                      out:
                        value: ${result.value}
                """, runtime(false));

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(7, result.variables().get("value"));
    }

    @Test
    void enforcesScriptDryRunReadiness() throws Exception {
        var rejected = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - script: js
                      body: result.set("value", 1);
                """, runtime(true));
        assertEquals(ProcessStatus.FAILED, rejected.status());
        assertEquals("Dry-run mode is not supported for this 'script' step", rejected.failure().message());

        var accepted = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - script: js
                      meta:
                        dryRunReady: ${true}
                      body: result.set("value", 1);
                      out:
                        value: ${result.value}
                """, runtime(true));
        assertEquals(ProcessStatus.SUCCEEDED, accepted.status(), String.valueOf(accepted.failure()));
        assertEquals(1, accepted.variables().get("value"));
    }
    @Test
    void executesScriptResourcesByFileExtension() throws Exception {
        ScriptRuntime.ResourceResolver resolver = reference -> {
            assertEquals("scripts/example.js", reference);
            return new java.io.StringReader("""
                    result.set("data", {text: greeting + " world", items: [1, 2]});
                    """);
        };
        var environment = new TaskEnvironment(null, Path.of("target/task-runtime-test"), false, false, Map.of(),
                null, null, null, null, null, null, List.of(), List.of(), Map.of(
                SensitiveDataHolder.class, new SensitiveValues(),
                ScriptRuntime.ResourceResolver.class, resolver));
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new Provider())), environment);
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - script: scripts/example.js
                      in:
                        greeting: hello
                      out:
                        text: ${result.data.text}
                        items: ${result.data.items}
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("hello world", result.variables().get("text"));
        assertEquals(List.of(1, 2), result.variables().get("items"));
    }

    @Test
    void appliesConfiguredTaskSchemaValidationModes() throws Exception {
        var validator = new JsonSchemaTaskValidator();
        var validatedRuntime = new TaskRuntime(new TaskRegistry(List.of(new Provider())),
                environment(false, new SensitiveValues(), Map.of()), validator, List.of());
        var invalidInput = run("""
                configuration:
                  runtime: concord-v2.5
                  validation:
                    taskCalls:
                      in: fail
                flows:
                  default:
                    - task: probe
                      in:
                        right: 2
                """, validatedRuntime);
        assertEquals(ProcessStatus.FAILED, invalidInput.status());
        assertTrue(invalidInput.failure().message().contains("Task 'probe' in validation errors"));
        assertTrue(invalidInput.failure().message().contains("left"));

        validatedRuntime = new TaskRuntime(new TaskRegistry(List.of(new Provider())),
                environment(false, new SensitiveValues(), Map.of()), validator, List.of());
        var invalidOutput = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    base: 5
                  validation:
                    taskCalls:
                      out: fail
                flows:
                  default:
                    - task: probe
                      in:
                        left: 1
                        right: 2
                """, validatedRuntime);
        assertEquals(ProcessStatus.FAILED, invalidOutput.status());
        assertTrue(invalidOutput.failure().message().contains("Task 'probe' out validation errors"),
                invalidOutput.failure().message());
        assertTrue(invalidOutput.failure().message().contains("answer"), invalidOutput.failure().message());

        validatedRuntime = new TaskRuntime(new TaskRegistry(List.of(new Provider())),
                environment(false, new SensitiveValues(), Map.of()), validator, List.of());
        var disabled = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    base: 5
                flows:
                  default:
                    - task: probe
                      in:
                        right: 2
                """, validatedRuntime);
        assertEquals(ProcessStatus.SUCCEEDED, disabled.status(), String.valueOf(disabled.failure()));
    }

    @Test
    void warnsInsteadOfFailingWhenSchemaOutputContainsInstant() {
        var validator = new JsonSchemaTaskValidator();

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> validator.validateOutput("probe", ProbeTask.class,
                Map.of("answer", "ok", "createdAt", Instant.now()), TaskRuntime.ValidationMode.WARN));
    }

    @Test
    void includesSchemaValidationDetailsInFailurePayload() {
        var validator = new JsonSchemaTaskValidator();

        var error = assertThrows(UserDefinedException.class, () -> validator.validateOutput("probe", ProbeTask.class,
                Map.of(), TaskRuntime.ValidationMode.FAIL));

        assertEquals("probe", error.getPayload().get("taskName"));
        assertEquals("out", error.getPayload().get("section"));
        assertEquals("probe.schema.json", error.getPayload().get("schemaResource"));
        assertFalse(((List<?>) error.getPayload().get("errors")).isEmpty());
    }

    @Test
    void excludesRunnerThreadIdFromStrictOutputValidation() throws Exception {
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new Provider())),
                environment(false, new SensitiveValues(), Map.of()), new JsonSchemaTaskValidator(), List.of());
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  validation:
                    taskCalls:
                      out: fail
                flows:
                  default:
                    - task: strict
                      out: result
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("value", ((Map<?, ?>) result.variables().get("result")).get("value"));
    }

    @Test
    void exposesConfiguredSdkPayloadAndExecutionState() throws Exception {
        var configuration = ProcessConfiguration.builder()
                .entryPoint("configured-entry-point")
                .processInfo(ProcessInfo.builder().sessionToken("session-token").build())
                .projectInfo(ProjectInfo.builder().orgName("configured-org").build())
                .initiator(Map.of("username", "initiator"))
                .currentUser(Map.of("username", "current-user"))
                .build();
        var compiler = (Compiler) (definition, step) -> (runtime, state, threadId) -> {
        };
        var environment = new TaskEnvironment(null, Path.of("target/task-runtime-test"), false, false, Map.of(),
                null, null, null, null, configuration, null, List.of(), List.of(), Map.of(Compiler.class, compiler));
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new PayloadProvider())), environment);

        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: payload
                      out: payload
                """, runtime);
        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        var values = (Map<?, ?>) result.variables().get("payload");
        assertEquals("configured-entry-point", values.get("entryPoint"));
        assertEquals("session-token", values.get("sessionToken"));
        assertEquals("configured-org", values.get("organization"));
        assertEquals("current-user", values.get("currentUser"));
        assertEquals(true, values.get("executionState"));
        assertEquals(true, values.get("compilerAvailable"));
    }

    @Test
    void invokesCustomTaskAndBeanMethodResolvers() throws Exception {
        var taskCalls = new AtomicInteger();
        var beanCalls = new AtomicInteger();
        var taskResolver = new CustomTaskMethodResolver() {
            @Override
            public TaskInvocation resolve(Task base, String method, Class<?>[] parameterTypes, Object[] parameters) {
                if (!(base instanceof ResolverTask) || !"virtual".equals(method)) {
                    return null;
                }
                return new TaskInvocation() {
                    @Override
                    public String taskName() {
                        return "resolver";
                    }

                    @Override
                    public Class<? extends Task> taskClass() {
                        return ResolverTask.class;
                    }

                    @Override
                    public Object invoke(com.walmartlabs.concord.runtime.v2.sdk.InvocationContext context) {
                        taskCalls.incrementAndGet();
                        return "mocked";
                    }
                };
            }
        };
        var beanResolver = (com.walmartlabs.concord.runtime.v2.sdk.CustomBeanMethodResolver)
                (base, method, parameterTypes, parameters) -> base instanceof Verifier && "check".equals(method)
                        ? context -> {
                            beanCalls.incrementAndGet();
                            return true;
                        }
                        : null;
        var environment = new TaskEnvironment(null, Path.of("target/task-runtime-test"), false, false, Map.of(),
                null, null, null, null, null, null, List.of(taskResolver), List.of(beanResolver), Map.of());
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new ResolverProvider())), environment);

        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: ${resolver.virtual()}
                      out: call
                    - expr: ${resolver.verifier().check()}
                      out: verified
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("mocked", result.variables().get("call"));
        assertEquals(true, result.variables().get("verified"));
        assertEquals(1, taskCalls.get());
        assertEquals(1, beanCalls.get());
    }

    @Test
    void preservesNullAndExpandedVarargsForTaskInterceptors() throws Exception {
        var arguments = new ArrayList<List<Object>>();
        TaskRuntime.TaskInterceptor interceptor = new TaskRuntime.TaskInterceptor() {
            @Override
            public <T> T invoke(Context context, String taskName, Class<? extends Task> taskClass,
                                String methodName, List<Object> invocationArguments,
                                java.util.concurrent.Callable<T> action) throws Exception {
                arguments.add(new ArrayList<>(invocationArguments));
                return action.call();
            }
        };
        var environment = new TaskEnvironment(null, Path.of("target/task-runtime-test"), false, false, Map.of(),
                null, null, null, null, null, interceptor, List.of(), List.of(), Map.of());
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new ResolverProvider())), environment);

        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: ${resolver.fixedAndVarargs(null, 'later')}
                      out: value
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(":later", result.variables().get("value"));
        assertEquals(java.util.Arrays.asList(null, "later"), arguments.getFirst());
    }

    @Test
    void registersSensitiveDataFromCustomMethodDelegation() throws Exception {
        var sensitiveData = new SensitiveValues();
        var resolver = new CustomTaskMethodResolver() {
            @Override
            public TaskInvocation resolve(Task base, String method, Class<?>[] parameterTypes, Object[] parameters) {
                if (!(base instanceof ResolverTask) || !"delegatedSecret".equals(method)) {
                    return null;
                }
                return new TaskInvocation() {
                    @Override
                    public String taskName() {
                        return "resolver";
                    }

                    @Override
                    public Class<? extends Task> taskClass() {
                        return ResolverTask.class;
                    }

                    @Override
                    public Object invoke(com.walmartlabs.concord.runtime.v2.sdk.InvocationContext context) {
                        return context.invoker().invoke(base, method, parameterTypes, parameters);
                    }
                };
            }
        };
        var environment = new TaskEnvironment(null, Path.of("target/task-runtime-test"), false, false, Map.of(),
                null, null, null, null, null, null, List.of(resolver), List.of(),
                Map.of(SensitiveDataHolder.class, sensitiveData));
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new ResolverProvider())), environment);

        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: ${resolver.delegatedSecret()}
                      out: secret
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("delegated-secret", result.variables().get("secret"));
        assertTrue(sensitiveData.get().contains("delegated-secret"));
    }

    @Test
    void reportsClearArityErrorsForVarargsMethodsMissingFixedArguments() throws Exception {
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new ResolverProvider())),
                environment(false, new SensitiveValues(), Map.of()));
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: ${resolver.needsFixed()}
                """, runtime);

        assertEquals(ProcessStatus.FAILED, result.status());
        assertTrue(result.failure().message().contains("expects at least 1 argument(s), got 0"),
                String.valueOf(result.failure()));
    }

    @Test
    void registersSensitiveExecuteOutputValues() throws Exception {
        var sensitiveData = new SensitiveValues();
        var environment = environment(false, sensitiveData, Map.of());
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new SensitiveOutputProvider())), environment);

        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: sensitive-output
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertTrue(sensitiveData.get().contains("task-secret"));
    }

    @Test
    void invokesConfiguredTaskInterceptor() throws Exception {
        var methods = new ArrayList<String>();
        TaskRuntime.TaskInterceptor interceptor = new TaskRuntime.TaskInterceptor() {
            @Override
            public <T> T invoke(Context context, String taskName, Class<? extends Task> taskClass,
                                String methodName, List<Object> arguments,
                                java.util.concurrent.Callable<T> action) throws Exception {
                methods.add(taskName + "." + methodName);
                return action.call();
            }
        };
        var environment = new TaskEnvironment(null, Path.of("target/task-runtime-test"), false, false,
                Map.of("probe", Map.of("left", 1, "right", 2)), null, null, null, null, null, interceptor,
                List.of(), List.of(), Map.of(SensitiveDataHolder.class, new SensitiveValues()));
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new Provider())), environment);

        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    base: 5
                flows:
                  default:
                    - task: probe
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(List.of("probe.execute"), methods);
    }

    @Test
    void loadsMockModuleWithoutV2RunnerListeners() {
        org.junit.jupiter.api.Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> Elements.getElements(new MockModule()));
    }

    @Test
    void executesMockTaskResult() throws Exception {
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new MockProvider())),
                environment(false, new SensitiveValues(), Map.of()));
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    mocks:
                      - task: mocked
                        out:
                          source: mock
                flows:
                  default:
                    - task: mocked
                      out: result
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("mock", ((Map<?, ?>) result.variables().get("result")).get("source"));
        assertEquals(true, ((Map<?, ?>) result.variables().get("result")).get("ok"));
    }

    @Test
    void executesMockedTaskInDryRunWhenOriginIsNotReady() throws Exception {
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new MockProvider())),
                environment(true, new SensitiveValues(), Map.of()));
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    mocks:
                      - task: mocked
                        out:
                          source: dry-run-mock
                flows:
                  default:
                    - task: mocked
                      out: result
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("dry-run-mock", ((Map<?, ?>) result.variables().get("result")).get("source"));
        assertEquals(true, ((Map<?, ?>) result.variables().get("result")).get("ok"));
    }


    @Test
    void surfacesMockTaskThrow() throws Exception {
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new MockProvider())),
                environment(false, new SensitiveValues(), Map.of()));
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    mocks:
                      - task: mocked
                        throwError: expected mock failure
                flows:
                  default:
                    - task: mocked
                """, runtime);

        assertEquals(ProcessStatus.FAILED, result.status());
        assertEquals("expected mock failure", result.failure().message());
    }


    private static ProcessResult run(String source, TaskRuntime runtime, int workerParallelism) throws Exception {
        var expressions = new ExpressionService(runtime);
        runtime.bind(expressions);
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream(
                source.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        return EngineFixture.engine(expressions, 256, runtime, workerParallelism,
                com.walmartlabs.concord.runtime.v25.runner.engine.RetryScheduler.SYSTEM)
                .run(plan, "default", Map.of(), result -> {
                });
    }
    @Test
    void normalizesThrownTaskExceptionsForIgnoreErrors() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: throwing
                      ignoreErrors: true
                      out: failed
                    - set:
                        continued: true
                """, runtime(false));

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(false, ((Map<?, ?>) result.variables().get("failed")).get("ok"));
        assertEquals("thrown task failure", ((Map<?, ?>) result.variables().get("failed")).get("error"));
        assertEquals(true, result.variables().get("continued"));
    }

    @Test
    void executesNestedTaskCallsWithoutReacquiringSaturatedPermit() {
        try (var executor = new InvocationExecutor(1, java.time.Duration.ofSeconds(5))) {
            var result = InvocationExecutor.withCurrent(executor,
                    () -> executor.call(() -> InvocationExecutor.callCurrent(() -> "nested")));

            assertEquals("nested", result);
        }
    }

    @Test
    void completesNestedParallelFlowsWithOneWorkerPermit() throws Exception {
        var result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                () -> run("""
                        configuration:
                          runtime: concord-v2.5
                        flows:
                          default:
                            - parallel:
                                - parallel:
                                    - set:
                                        left: left
                                    - set:
                                        right: right
                                  out: [left, right]
                                - set:
                                    third: third
                              out: [left, right, third]
                        """, runtime(false), 1));

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("left", result.variables().get("left"));
        assertEquals("right", result.variables().get("right"));
        assertEquals("third", result.variables().get("third"));
    }

    @Test
    void propagatesCompleteStepContextToTaskWorkersWithoutBleed() throws Exception {
        var contexts = new ArrayList<TaskRuntime.StepContext>();
        var runtime = new TaskRuntime(new TaskRegistry(List.of(new Provider())),
                environment(false, new SensitiveValues(), Map.of()), TaskRuntime.Validator.NONE,
                List.of(new TaskRuntime.TaskHook() {
                    @Override
                    public void before(TaskRuntime.Invocation invocation) {
                        contexts.add(invocation.step());
                        assertTrue(Thread.currentThread().isVirtual());
                    }
                }));

        var result = run("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    base: 1
                flows:
                  default:
                    - task: probe
                      meta:
                        marker: first
                    - task: probe
                      meta:
                        marker: second
                """, runtime);

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals(2, contexts.size());
        assertEquals("concord.yml", contexts.get(0).source());
        assertTrue(contexts.get(0).line() > 0);
        assertTrue(contexts.get(0).column() > 0);
        assertNotNull(contexts.get(0).processDefinitionId());
        assertNotNull(contexts.get(0).correlationId());
        assertEquals("first", ((Map<?, ?>) contexts.get(0).metadata().get("meta")).get("marker"));
        assertEquals("second", ((Map<?, ?>) contexts.get(1).metadata().get("meta")).get("marker"));
        assertFalse(contexts.get(0).correlationId().equals(contexts.get(1).correlationId()));
    }

    @Test
    void releasesPermitsForMoreThanSixtyFourSequentialInvocations() {
        try (var executor = new InvocationExecutor(64, java.time.Duration.ofSeconds(5))) {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
                for (var i = 0; i < 65; i++) {
                    var expected = i;
                    assertEquals(expected, executor.call(() -> expected));
                }
            });
        }
    }

    @Test
    void closesEachPolyglotEngineAfterScriptEvaluation() throws Exception {
        var polyglotEngines = new ArrayList<org.graalvm.polyglot.Engine>();
        var closedEngines = new AtomicInteger();
        var runtime = new ScriptRuntime(new TaskRegistry(List.of()),
                environment(false, new SensitiveValues(), Map.of()), new ScriptEngineManager(),
                (engine, context) -> {
                    polyglotEngines.add(engine);
                    return com.oracle.truffle.js.scriptengine.GraalJSScriptEngine.create(engine, context);
                }, ignored -> closedEngines.incrementAndGet());

        assertEquals(Map.of("value", 1), runtime.evaluate(scriptContext(), "js",
                new StringReader("result.set('value', 1);"), Map.of()));
        assertEquals(Map.of("value", 2), runtime.evaluate(scriptContext(), "js",
                new StringReader("result.set('value', 2);"), Map.of()));
        assertEquals(2, polyglotEngines.size());
        assertEquals(2, closedEngines.get());
    }

    @Test
    void packsTaskMethodVarargsForReflection() throws Exception {
        var result = run("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - expr: ${probe.join('prefix', 'one', 'two')}
                      out: joined
                """, runtime(false));

        assertEquals(ProcessStatus.SUCCEEDED, result.status(), String.valueOf(result.failure()));
        assertEquals("prefix:one,two", result.variables().get("joined"));
    }

    private static TaskRuntime runtime(boolean dryRun) {
        return new TaskRuntime(new TaskRegistry(List.of(new Provider())),
                environment(dryRun, new SensitiveValues(), Map.of()));
    }

    private static TaskEnvironment environment(boolean dryRun, SensitiveDataHolder sensitiveData,
                                               Map<String, Map<String, Object>> defaults) {
        return new TaskEnvironment(null, Path.of("target/task-runtime-test"), false, dryRun, defaults,
                null, null, null, null, null, null, List.of(), List.of(),
                Map.of(SensitiveDataHolder.class, sensitiveData));
    }

    private static ProcessResult run(String source, TaskRuntime runtime) throws Exception {
        var expressions = new ExpressionService(runtime);
        runtime.bind(expressions);
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream(
                source.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        return EngineFixture.engine(expressions, runtime).run(plan, "default", Map.of(), result -> {
        });
    }

    private static ScriptRuntime scriptRuntime(RecordingScriptEngine engine) {
        var engines = new ScriptEngineManager();
        engines.registerEngineName("close-test", engine.factory());
        return new ScriptRuntime(new TaskRegistry(List.of()),
                environment(false, new SensitiveValues(), Map.of()), engines);
    }

    private static Context scriptContext() {
        return (Context) Proxy.newProxyInstance(Context.class.getClassLoader(), new Class<?>[]{Context.class},
                (proxy, method, arguments) -> {
                    if ("variables".equals(method.getName())) {
                        return new MapBackedVariables(Map.of());
                    }
                    if ("toString".equals(method.getName())) {
                        return "script-context";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class RecordingScriptEngine {
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final ScriptException evaluationFailure;
        private final RuntimeException closeFailure;

        private RecordingScriptEngine(ScriptException evaluationFailure, RuntimeException closeFailure) {
            this.evaluationFailure = evaluationFailure;
            this.closeFailure = closeFailure;
        }

        private ScriptEngineFactory factory() {
            return new ScriptEngineFactory() {
                @Override
                public String getEngineName() {
                    return "recording";
                }

                @Override
                public String getEngineVersion() {
                    return "1";
                }

                @Override
                public List<String> getExtensions() {
                    return List.of();
                }

                @Override
                public List<String> getMimeTypes() {
                    return List.of();
                }

                @Override
                public List<String> getNames() {
                    return List.of("close-test");
                }

                @Override
                public String getLanguageName() {
                    return "recording";
                }

                @Override
                public String getLanguageVersion() {
                    return "1";
                }

                @Override
                public Object getParameter(String key) {
                    return null;
                }

                @Override
                public String getMethodCallSyntax(String object, String method, String... arguments) {
                    return null;
                }

                @Override
                public String getOutputStatement(String toDisplay) {
                    return null;
                }

                @Override
                public String getProgram(String... statements) {
                    return null;
                }

                @Override
                public ScriptEngine getScriptEngine() {
                    return scriptEngine();
                }
            };
        }

        private ScriptEngine scriptEngine() {
            var context = new SimpleScriptContext();
            return (ScriptEngine) Proxy.newProxyInstance(ScriptEngine.class.getClassLoader(),
                    new Class<?>[]{ScriptEngine.class, AutoCloseable.class}, (proxy, method, arguments) -> {
                        return switch (method.getName()) {
                            case "getContext" -> context;
                            case "createBindings" -> new SimpleBindings();
                            case "eval" -> {
                                if (evaluationFailure != null) {
                                    throw evaluationFailure;
                                }
                                yield null;
                            }
                            case "close" -> {
                                closeCalls.incrementAndGet();
                                if (closeFailure != null) {
                                    throw closeFailure;
                                }
                                yield null;
                            }
                            case "setBindings" -> null;
                            case "toString" -> "recording-engine";
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    });
        }
    }

    private static final class SensitiveOutputProvider implements TaskProvider {

        @Override
        public Task createTask(Context context, String key) {
            return "sensitive-output".equals(key) ? new SensitiveOutputTask() : null;
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return "sensitive-output".equals(key) ? SensitiveOutputTask.class : null;
        }

        @Override
        public boolean hasTask(String key) {
            return "sensitive-output".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("sensitive-output");
        }
    }

    private static final class SensitiveOutputTask implements Task {

        @Override
        @SensitiveData(keys = "secret.nested", includeNestedValues = true)
        public TaskResult execute(Variables input) {
            return TaskResult.success().value("secret", Map.of("nested", List.of("task-secret")));
        }
    }

    private static final class ResolverProvider implements TaskProvider {

        @Override
        public Task createTask(Context context, String key) {
            return "resolver".equals(key) ? new ResolverTask() : null;
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return "resolver".equals(key) ? ResolverTask.class : null;
        }

        @Override
        public boolean hasTask(String key) {
            return "resolver".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("resolver");
        }
    }

    public static final class ResolverTask implements Task {

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success();
        }

        public String call() {
            return "actual";
        }

        public Verifier verifier() {
            return new Verifier();
        }

        public String needsFixed(String value, String... suffix) {
            return value;
        }

        public String fixedAndVarargs(String fixed, String... values) {
            return fixed + ":" + String.join(",", values);
        }

        @SensitiveData
        public String delegatedSecret() {
            return "delegated-secret";
        }
    }

    private static final class Verifier {

        public boolean check() {
            return false;
        }
    }

    private static final class PayloadProvider implements TaskProvider {

        @Override
        public Task createTask(Context context, String key) {
            return "payload".equals(key) ? new PayloadTask(context) : null;
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return "payload".equals(key) ? PayloadTask.class : null;
        }

        @Override
        public boolean hasTask(String key) {
            return "payload".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("payload");
        }
    }

    private static final class PayloadTask implements Task {

        private final Context context;

        private PayloadTask(Context context) {
            this.context = context;
        }

        @Override
        public TaskResult execute(Variables input) {
            var configuration = context.processConfiguration();
        return TaskResult.success()
                .value("entryPoint", configuration.entryPoint())
                .value("sessionToken", configuration.processInfo().sessionToken())
                .value("organization", configuration.projectInfo().orgName())
                .value("currentUser", configuration.currentUser().get("username"))
                .value("executionState", context.execution().state() != null)
                .value("compilerAvailable", context.compiler() != null
                        && context.execution().runtime().getService(Compiler.class) != null);
        }
    }

    private static final class MockProvider implements TaskProvider {

        @Override
        public Task createTask(Context context, String key) {
            if (!"mocked".equals(key)) {
                return null;
            }
            return new MockTask(context, key, new MockDefinitionProvider(), Task.class,
                    () -> new Task() {
                        @Override
                        public TaskResult execute(Variables input) {
                            return TaskResult.success();
                        }
                    });
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return "mocked".equals(key) ? MockTask.class : null;
        }

        @Override
        public boolean hasTask(String key) {
            return "mocked".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("mocked");
        }
    }

    private static final class RetryInputProvider implements TaskProvider {
        @Override
        public Task createTask(Context context, String key) {
            return "retry-input".equals(key) ? new RetryInputTask() : null;
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return "retry-input".equals(key) ? RetryInputTask.class : null;
        }

        @Override
        public boolean hasTask(String key) {
            return "retry-input".equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("retry-input");
        }
    }

    private static final class RetryInputTask implements Task {
        private static final AtomicInteger calls = new AtomicInteger();

        @Override
        public TaskResult execute(Variables input) {
            if (calls.getAndIncrement() == 0) {
                return TaskResult.fail("retry");
            }
            return TaskResult.success().value("config", input.get("config"));
        }
    }

    private static final class Provider implements TaskProvider {

        @Override
        public Task createTask(Context ctx, String key) {
            return switch (key) {
                case "probe" -> new ProbeTask(ctx);
                case "nested" -> new NestedTask(ctx);
                case "fail", "suspend", "throwing" -> new ReadyTask(key);
                case "strict" -> new StrictOutputTask();
                case "unsafe" -> new UnsafeTask();
                default -> null;
            };
        }

        @Override
        public Class<? extends Task> getTaskClass(Context ctx, String key) {
            return switch (key) {
                case "probe" -> ProbeTask.class;
                case "nested" -> NestedTask.class;
                case "fail", "suspend", "throwing" -> ReadyTask.class;
                case "strict" -> StrictOutputTask.class;
                case "unsafe" -> UnsafeTask.class;
                default -> null;
            };
        }

        @Override
        public boolean hasTask(String key) {
            return names().contains(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("probe", "nested", "fail", "suspend", "throwing", "strict", "unsafe");
        }
    }

    private static final class NestedTask implements Task {

        private final Context context;

        private NestedTask(Context context) {
            this.context = context;
        }

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success().value("nested",
                    context.nestedFlowExecutor().execute("child", Map.of("child", "child-value")));
        }
    }

    @DryRunReady
    private static final class ReadyTask implements Task {

        private final String name;

        private ReadyTask(String name) {
            this.name = name;
        }

        @Override
        public TaskResult execute(Variables input) {
            return switch (name) {
                case "fail" -> TaskResult.fail("expected failure").value("partial", 42);
                case "suspend" -> TaskResult.suspend("wait-for-test");
                case "throwing" -> throw new IllegalStateException("thrown task failure");
                default -> throw new IllegalStateException("Unexpected task: " + name);
            };
        }
    }

    private static final class UnsafeTask implements Task {
        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success();
        }

        public String message() {
            return "unsafe";
        }
    }

    private static final class StrictOutputTask implements Task {
        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success().value("value", "value");
        }
    }

    @DryRunReady
    public static final class ProbeTask implements Task {

        private final Context context;

        private ProbeTask(Context context) {
            this.context = context;
        }

        @Override
        public TaskResult execute(Variables input) {
            var step = (TaskCall) context.execution().currentStep();
            return TaskResult.success()
                    .value("left", input.get("left"))
                    .value("right", input.get("right"))
                    .value("visibleBase", context.variables().get("base"))
                    .value("evaluated", context.eval("${base + 3}", Long.class))
                    .value("virtualThread", Thread.currentThread().isVirtual())
                    .value("currentTask", step.getName())
                    .value("processDefinitionRuntime",
                            context.execution().runtime().getService(ProcessDefinition.class)
                                    .configuration().runtime())
                    .value("currentThreadPresent", context.execution().currentThreadId() != null)
                    .value("currentStepFile", step.getLocation().fileName())
                    .value("currentStepMeta", step.getOptions().meta().get("compatibilityMarker"));
        }

        public long add(Number left, Number right) {
            return left.longValue() + right.longValue();
        }

        public String echo(@SensitiveData String value) {
            return value;
        }

        public String join(String prefix, String... values) {
            return prefix + ":" + String.join(",", values);
        }
    }

    private static final class SensitiveValues implements SensitiveDataHolder {

        private final Set<String> values = new LinkedHashSet<>();

        @Override
        public Set<String> get() {
            return Set.copyOf(values);
        }

        @Override
        public void add(String sensitiveData) {
            values.add(sensitiveData);
        }

        @Override
        public void addAll(java.util.Collection<String> sensitiveData) {
            values.addAll(sensitiveData);
        }
    }
}
