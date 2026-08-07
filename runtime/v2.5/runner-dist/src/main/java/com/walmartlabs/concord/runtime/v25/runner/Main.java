package com.walmartlabs.concord.runtime.v25.runner;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.common.ProcessHeartbeat;
import com.walmartlabs.concord.runtime.common.SensitiveDataMasker;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.CustomBeanMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.runtime.v2.sdk.FileService;
import com.walmartlabs.concord.runtime.v2.sdk.LockService;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.runtime.v25.model.Configuration25;
import com.walmartlabs.concord.runtime.v25.model.Definition25;
import com.walmartlabs.concord.runtime.v25.model.PlanValidator25;
import com.walmartlabs.concord.runtime.v25.model.ProjectLoader25;
import com.walmartlabs.concord.runtime.v25.runner.engine.Engine;
import com.walmartlabs.concord.runtime.v25.runner.engine.LifecycleEvent;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessResult;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessStatus;
import com.walmartlabs.concord.runtime.v25.runner.engine.StatusCallback;
import com.walmartlabs.concord.runtime.v25.runner.engine.RetryScheduler;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.persistence.CheckpointStore;
import com.walmartlabs.concord.runtime.v25.runner.persistence.FileCheckpointStore;
import com.walmartlabs.concord.runtime.v25.runner.persistence.State25;
import com.walmartlabs.concord.runtime.v25.runner.plan.ExecutionPlan;
import com.walmartlabs.concord.runtime.v25.runner.plan.PlanCompiler;
import com.walmartlabs.concord.runtime.v25.runner.task.JsonSchemaTaskValidator;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskEnvironment;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskPolicyHook;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRegistry;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import com.walmartlabs.concord.sdk.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collection;
import java.util.function.Consumer;

public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    /**
     * SDK services provided by the local launcher rather than remote runner adapters.
     */
    public record LocalServices(DockerService dockerService, SecretService secretService, LockService lockService,
                                FileService fileService) {
        public LocalServices {
            Objects.requireNonNull(dockerService, "dockerService");
            Objects.requireNonNull(secretService, "secretService");
            Objects.requireNonNull(lockService, "lockService");
            Objects.requireNonNull(fileService, "fileService");
        }
    }

    /**
     * Optional callbacks for embedding the v2.5 runner in a local client.
     */
    public record LocalCallbacks(Consumer<LifecycleEvent> lifecycleListener,
                                 List<? extends TaskRuntime.TaskHook> taskHooks) {

        public static final LocalCallbacks NONE = new LocalCallbacks(event -> { }, List.of());

        public LocalCallbacks {
            lifecycleListener = lifecycleListener != null ? lifecycleListener : event -> { };
            taskHooks = List.copyOf(taskHooks != null ? taskHooks : List.of());
        }
    }

    public static void main(String[] args) throws Exception {
        var runnerConfiguration = loadRunnerConfiguration(args);
        var runtime = RunnerWiring.create(runnerConfiguration);
        var processConfiguration = runtime.processConfiguration();
        Objects.requireNonNull(processConfiguration.instanceId(), "ProcessConfiguration.instanceId");
        var heartbeat = new ProcessHeartbeat(runtime.apiClient(), processConfiguration.instanceId(),
                runnerConfiguration.api().maxNoHeartbeatInterval());
        heartbeat.start();
        int exitCode;
        try {
            var result = execute(runtime, runnerConfiguration);
            exitCode = exitCode(result.status());
        } catch (Throwable e) {
            log.error("Runtime v2.5 failed", e);
            exitCode = 1;
        } finally {
            heartbeat.stop();
        }
        System.exit(exitCode);
    }

    static ProcessResult execute(RunnerWiring.Runtime runtime, RunnerConfiguration runnerConfiguration) throws Exception {
        var processConfiguration = runtime.processConfiguration();
        var workDirectory = runtime.workingDirectory().getValue();
        var logging = new RunnerLogging(runnerConfiguration, runtime.apiClient(), processConfiguration.instanceId(),
                runtime.sensitiveData(), workDirectory);
        logging.start();
        try (var callback = new RunnerCallback(runnerConfiguration, processConfiguration, runtime.apiClient(),
                runtime.sensitiveData(), logging, workDirectory)) {
            callback.running();
            return execute(runtime, runnerConfiguration, callback, true, logging, List.of());
        } catch (Throwable e) {
            logging.finish(ProcessStatus.FAILED);
            throw e;
        }
    }

    public static ProcessResult executeLocal(Path workDirectory, RunnerConfiguration runnerConfiguration,
                                             ProcessConfiguration processConfiguration, TaskProvider taskProvider,
                                             SensitiveDataHolder sensitiveData, LocalServices services,
                                             List<CustomTaskMethodResolver> taskMethodResolvers,
                                             List<CustomBeanMethodResolver> beanMethodResolvers) throws Exception {
        return executeLocal(workDirectory, runnerConfiguration, processConfiguration, taskProvider, sensitiveData, services,
                taskMethodResolvers, beanMethodResolvers, Map.of(), LocalCallbacks.NONE);
    }

    public static ProcessResult executeLocal(Path workDirectory, RunnerConfiguration runnerConfiguration,
                                             ProcessConfiguration processConfiguration, TaskProvider taskProvider,
                                             SensitiveDataHolder sensitiveData, LocalServices services,
                                             List<CustomTaskMethodResolver> taskMethodResolvers,
                                             List<CustomBeanMethodResolver> beanMethodResolvers,
                                             Map<String, Method> functions, LocalCallbacks callbacks) throws Exception {
        var runtime = new RunnerWiring.Runtime(null, new WorkingDirectory(workDirectory), processConfiguration,
                null, sensitiveData, null, List.of(taskProvider), taskMethodResolvers, beanMethodResolvers, functions,
                services.dockerService(), services.secretService(), services.lockService(), services.fileService(), null);
        StatusCallback callback = new StatusCallback() {
            @Override
            public void onEvent(LifecycleEvent event) {
                callbacks.lifecycleListener().accept(event);
            }

            @Override
            public void onTerminal(ProcessResult result) {
                if (result.status() == ProcessStatus.FAILED) {
                    var failure = SensitiveDataMasker.mask(RunnerCallback.FailureRenderer.render(result.failure()),
                            sensitiveData.get());
                    System.err.println(failure);
                }
            }
        };
        return execute(runtime, runnerConfiguration, callback, false, null, callbacks.taskHooks());
    }

    private static ProcessResult execute(RunnerWiring.Runtime runtime, RunnerConfiguration runnerConfiguration,
                                         StatusCallback callback, boolean remote, RunnerLogging logging,
                                         Collection<? extends TaskRuntime.TaskHook> localTaskHooks) throws Exception {
        var processConfiguration = runtime.processConfiguration();
        var workDirectory = runtime.workingDirectory().getValue();
        if (processConfiguration.debug()) {
            log.info("Available tasks: {}", runtime.taskProviders().stream()
                    .flatMap(provider -> provider.names().stream()).distinct().sorted().toList());
        }

        var definition = loadDefinition(workDirectory, processConfiguration);
        var checkpointFile = checkpointFile(workDirectory);
        var localStore = new FileCheckpointStore(checkpointFile, runtime.dependencyClassLoader());
        CheckpointStore checkpointStore = remote
                ? new RemoteCheckpointStore(localStore, checkpointFile, runnerConfiguration,
                        processConfiguration.instanceId(), runtime.apiClient(), runtime.persistenceService(),
                        runtime.sensitiveData())
                : localStore;
        var sensitiveDataPersistence = remote
                ? runtime.persistenceService()
                : new RunnerWiring.PersistenceService(workDirectory, RunnerWiring.objectMapper());
        var hooks = new ArrayList<TaskRuntime.TaskHook>();
        hooks.add(new TaskPolicyHook(policyEngine(workDirectory)));
        hooks.addAll(localTaskHooks);
        if (callback instanceof RunnerCallback runnerCallback) {
            hooks.add(new RunnerTaskEventHook(runnerCallback));
        }
        var taskRuntime = new TaskRuntime(new TaskRegistry(runtime.taskProviders()),
                environment(runtime, runnerConfiguration, workDirectory), new JsonSchemaTaskValidator(), hooks);
        var expressions = new ExpressionService(taskRuntime, runtime.functions());
        new PlanValidator25(expressions::compile).validate(definition);
        var plan = new PlanCompiler(expressions).compile(definition);
        if (!processConfiguration.out().isEmpty()) {
            var configuration = new LinkedHashMap<>(plan.configuration().values());
            configuration.put("out", processConfiguration.out());
            plan = new ExecutionPlan(plan.id(), new Configuration25(configuration), plan.flows(),
                    plan.publicFlows(), plan.forms());
        }
        var forms = new FormService(workDirectory.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME).resolve(Constants.Files.JOB_FORMS_V2_DIR_NAME));
        var engine = new Engine(expressions, 256, taskRuntime, 64, RetryScheduler.SYSTEM,
                Duration.ofSeconds(5), checkpointStore, forms);

        var events = StateManager.readResumeEvents(workDirectory);
        var state = loadPersistedState(localStore);
        if (logging != null && state != null) {
            logging.restoreSegments(state.logSegments());
        }
        var resuming = events != null && !events.isEmpty();
        var restarting = restartableCheckpoint(state);
        if (resuming || restarting) {
            if (remote) {
                sensitiveDataPersistence.mergeSessionFile(Constants.Files.SENSITIVE_DATA_FILE_NAME,
                        runtime.sensitiveData());
            } else {
                sensitiveDataPersistence.merge(sensitiveDataFile(checkpointFile), runtime.sensitiveData());
            }
        }
        if (resuming) {
            validateResumeMarkerGeneration(workDirectory, localStore);
        }
        ProcessResult result;
        try {
            if (resuming) {
                if (state == null) {
                    throw new IllegalStateException("Resume event received without persisted runtime v2.5 state");
                }
                var input = processArguments(processConfiguration, workDirectory, RunnerWiring.objectMapper(), true, Map.of());
                result = engine.resume(plan, state, events, input, callback);
            } else if (restarting) {
                result = engine.restart(plan, state, callback);
            } else {
                var input = processArguments(processConfiguration, workDirectory, RunnerWiring.objectMapper(), false,
                        parentInput(state));
                result = engine.run(plan, processConfiguration.entryPoint(), input, callback);
            }
        } finally {
            if (remote) {
                sensitiveDataPersistence.persistSessionFile(Constants.Files.SENSITIVE_DATA_FILE_NAME,
                        runtime.sensitiveData().get());
            } else {
                sensitiveDataPersistence.persist(sensitiveDataFile(checkpointFile), runtime.sensitiveData().get());
            }
        }

        persistOutputs(workDirectory, RunnerWiring.objectMapper(), result.outputs());
        if (result.status() == ProcessStatus.SUSPENDED) {
            var persisted = localStore.load();
            if (persisted == null) {
                throw new IllegalStateException("Suspended process produced no durable runtime state");
            }
            if (logging != null) {
                persisted = logging.snapshot(persisted);
                localStore.save("suspend", persisted);
            }
            var outstanding = outstandingEvents(persisted);
            if (outstanding.isEmpty()) {
                throw new IllegalStateException("Suspended process produced no outstanding events");
            }
            StateManager.finalizeSuspendedState(workDirectory, outstanding);
            Files.deleteIfExists(resumeMarkerGenerationFile(workDirectory));
        } else {
            StateManager.cleanupState(workDirectory);
        }
        if (callback instanceof RunnerCallback runnerCallback) {
            runnerCallback.finish(result);
        }
        return result;
    }

    private static int exitCode(ProcessStatus status) {
        return switch (status) {
            case SUCCEEDED, SUSPENDED -> 0;
            case FAILED, CANCELLED, TIMED_OUT -> 1;
            case RUNNING -> throw new IllegalArgumentException("RUNNING is not terminal");
        };
    }

    private static boolean restartableCheckpoint(State25 state) {
        return state != null && state.waits().isEmpty() && state.checkpointName() != null
                && !"suspend".equals(state.checkpointName());
    }

    private static Map<String, Object> parentInput(State25 state) {
        if (state == null || state.root() == null) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        state.root().scopes().forEach(scope -> result.putAll(scope.overlay()));
        return result;
    }

    static void validateResumeMarkerGeneration(Path workDirectory, FileCheckpointStore store) throws IOException {
        var current = store.generation();
        if (current == null) {
            throw new IllegalStateException("Resume event received without a durable runtime v2.5 state generation");
        }
        var marker = resumeMarkerGenerationFile(workDirectory);
        if (Files.exists(marker)) {
            var expected = Files.readString(marker).trim();
            if (!current.equals(expected)) {
                throw new IllegalStateException("Resume marker belongs to an older runtime v2.5 state generation; "
                        + "the process must be resumed again with a fresh event");
            }
            return;
        }
        var parent = marker.getParent();
        Files.createDirectories(parent);
        var temporary = Files.createTempFile(parent, marker.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, current);
            Files.move(temporary, marker, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path resumeMarkerGenerationFile(Path workDirectory) {
        return workDirectory.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME).resolve(".runtime-v2.5-resume-generation");
    }

    private static LinkedHashSet<String> outstandingEvents(State25 state) {
        var result = new LinkedHashSet<String>();
        state.waits().forEach(wait -> result.add(wait.eventName()));
        collectOutstandingEvents(state.root(), result);
        return result;
    }

    private static void collectOutstandingEvents(State25.FiberState fiber, LinkedHashSet<String> result) {
        if (fiber == null) {
            return;
        }
        for (var frame : fiber.continuation()) {
            if (!(frame instanceof State25.StepState step) || step.parallel() == null) {
                continue;
            }
            for (var child : step.parallel().children()) {
                child.waits().forEach(wait -> result.add(wait.eventName()));
                collectOutstandingEvents(child.fiber(), result);
            }
        }
    }

    private static Definition25 loadDefinition(Path workDirectory, ProcessConfiguration processConfiguration) throws Exception {
        var loaded = new ProjectLoader25(new NoopImportManager()).loadProject(workDirectory, Definition25.RUNTIME_TYPE,
                imports -> imports, ImportsListener.NOP_LISTENER);
        return ((Definition25) loaded.projectDefinition()).effective(processConfiguration.processInfo().activeProfiles());
    }

    private static Map<String, Object> processArguments(ProcessConfiguration configuration, Path workDirectory,
                                                        ObjectMapper mapper, boolean resuming,
                                                        Map<String, Object> inherited) {
        var result = new LinkedHashMap<String, Object>(inherited);
        result.putAll(configuration.arguments());
        result.put(Constants.Context.TX_ID_KEY, configuration.instanceId().toString());
        result.put(Constants.Context.WORK_DIR_KEY, workDirectory.toAbsolutePath().toString());
        result.put(Constants.Request.PROCESS_INFO_KEY, mapper.convertValue(configuration.processInfo(), Map.class));
        result.put(Constants.Request.PROJECT_INFO_KEY, mapper.convertValue(configuration.projectInfo(), Map.class));
        if (configuration.initiator() != null) {
            result.put(Constants.Request.INITIATOR_KEY, configuration.initiator());
            if (!resuming) {
                result.put(Constants.Request.CURRENT_USER_KEY, configuration.initiator());
            }
        }
        if (resuming && configuration.currentUser() != null) {
            result.put(Constants.Request.CURRENT_USER_KEY, configuration.currentUser());
        }
        return result;
    }

    private static TaskEnvironment environment(RunnerWiring.Runtime runtime, RunnerConfiguration runnerConfiguration,
                                               Path workDirectory) {
        var configuration = runtime.processConfiguration();
        return new TaskEnvironment(configuration.instanceId(), workDirectory, configuration.debug(), configuration.dryRun(),
                configuration.defaultTaskVariables(), runtime.dockerService(), runtime.secretService(), runtime.lockService(),
                new RunnerSdkApiConfiguration(runnerConfiguration), configuration, null, runtime.taskMethodResolvers(),
                runtime.beanMethodResolvers(), Map.of(SensitiveDataHolder.class, runtime.sensitiveData()), runtime.fileService());
    }

    private static PolicyEngine policyEngine(Path workDirectory) throws IOException {
        var file = workDirectory.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(Constants.Files.POLICY_FILE_NAME);
        var rules = Files.exists(file)
                ? RunnerWiring.objectMapper().readValue(file.toFile(), PolicyEngineRules.class)
                : PolicyEngineRules.builder().build();
        return new PolicyEngine(rules);
    }

    static State25 loadPersistedState(FileCheckpointStore checkpointStore) throws IOException {
        return checkpointStore.load();
    }

    private static Path checkpointFile(Path workDirectory) {
        return workDirectory.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME).resolve("runtime-v2.5.state");
    }

    private static Path sensitiveDataFile(Path stateFile) {
        return stateFile.resolveSibling(stateFile.getFileName() + ".secrets.json");
    }

    private static void persistOutputs(Path workDirectory, ObjectMapper mapper, Map<String, Object> outputs)
            throws IOException {
        if (outputs.isEmpty()) {
            return;
        }
        var file = workDirectory.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.OUT_VALUES_FILE_NAME);
        Files.createDirectories(file.getParent());
        var result = Files.exists(file)
                ? mapper.readValue(file.toFile(), new TypeReference<Map<String, Object>>() { })
                : new LinkedHashMap<String, Object>();
        result.putAll(outputs);
        mapper.writeValue(file.toFile(), result);
    }

    private static RunnerConfiguration loadRunnerConfiguration(String[] args) throws IOException {
        RunnerConfiguration result = RunnerConfiguration.builder().build();
        if (args.length > 0) {
            try (InputStream input = Files.newInputStream(Paths.get(args[0]))) {
                result = RunnerWiring.objectMapper().readValue(input, RunnerConfiguration.class);
            }
        }
        var agentId = System.getenv("RUNNER_AGENT_ID");
        if (agentId != null) {
            result = RunnerConfiguration.builder().from(result).agentId(agentId).build();
        }
        var apiBaseUrl = System.getenv("RUNNER_API_BASE_URL");
        if (apiBaseUrl != null) {
            result = RunnerConfiguration.builder().from(result)
                    .api(ApiConfiguration.builder().from(result.api()).baseUrl(apiBaseUrl).build()).build();
        }
        if (result.agentId() == null || result.api() == null || result.api().baseUrl() == null) {
            throw new IllegalArgumentException("Specify a runner configuration file or RUNNER_AGENT_ID and RUNNER_API_BASE_URL");
        }
        return result;
    }

    private record RunnerSdkApiConfiguration(RunnerConfiguration runnerConfiguration)
            implements com.walmartlabs.concord.runtime.v2.sdk.ApiConfiguration {
        @Override
        public String baseUrl() {
            return runnerConfiguration.api().baseUrl();
        }

        @Override
        public int connectTimeout() {
            return runnerConfiguration.api().connectTimeout();
        }

        @Override
        public int readTimeout() {
            return runnerConfiguration.api().readTimeout();
        }
    }

    private Main() {
    }

}
