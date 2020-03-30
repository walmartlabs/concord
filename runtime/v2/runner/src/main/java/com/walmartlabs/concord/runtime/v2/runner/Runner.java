package com.walmartlabs.concord.runtime.v2.runner;

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

import com.google.inject.Injector;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.common.injector.WorkingDirectory;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.compiler.CompilerUtils;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.runner.vars.GlobalVariablesImpl;
import com.walmartlabs.concord.runtime.v2.runner.vm.UpdateGlobalVariablesCommand;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

public class Runner {

    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    private final UUID instanceId;
    private final Path workDir;
    private final Compiler compiler;
    private final ContextFactory contextFactory;
    private final TaskProviders taskProviders;
    private final ExpressionEvaluator expressionEvaluator;
    private final CheckpointService checkpointService;
    private final FormService formService;
    private final SynchronizationService synchronizationService;
    private final ProcessStatusCallback statusCallback;
    private final Collection<ExecutionListener> listeners;

    private Runner(Builder b) {
        this.instanceId = b.instanceId;
        this.workDir = b.workDir;
        this.compiler = b.compiler;
        this.contextFactory = b.contextFactory;
        this.taskProviders = b.taskProviders;
        this.expressionEvaluator = b.expressionEvaluator;
        this.checkpointService = b.checkpointService;
        this.formService = b.formService;
        this.synchronizationService = b.synchronizationService;
        this.statusCallback = b.statusCallback;
        this.listeners = b.listeners;
    }

    public ProcessSnapshot start(String entryPoint, Map<String, Object> input) throws Exception {
        statusCallback.onRunning(instanceId);
        log.debug("start ['{}'] -> running...", entryPoint);

        // assume all imports were processed by the agent
        ProjectLoaderV2 loader = new ProjectLoaderV2(new NoopImportManager());
        ProcessDefinition processDefinition = loader.load(workDir, new NoopImportsNormalizer()).getProjectDefinition();

        Command cmd = CompilerUtils.compile(compiler, processDefinition, entryPoint);
        State state = new InMemoryState(cmd);

        GlobalVariables globalVariables = new GlobalVariablesImpl();

        VM vm = createVM(processDefinition, globalVariables);
        // update the global variables using the input map by running a special command
        vm.run(state, new UpdateGlobalVariablesCommand(input));
        // start the normal execution
        vm.start(state);

        log.debug("start ['{}'] -> done", entryPoint);

        return ProcessSnapshot.builder()
                .vmState(state)
                .processDefinition(processDefinition)
                .globalVariables(globalVariables)
                .build();
    }

    public ProcessSnapshot resume(ProcessSnapshot snapshot, String eventRef, Map<String, Object> input) throws Exception {
        statusCallback.onRunning(instanceId);
        log.debug("resume ['{}'] -> running...", eventRef);

        State state = snapshot.vmState();
        GlobalVariables globalVariables = new GlobalVariablesImpl(snapshot.globalVariables().toMap());

        VM vm = createVM(snapshot.processDefinition(), globalVariables);
        // update the global variables using the input map by running a special command
        vm.run(state, new UpdateGlobalVariablesCommand(input));
        // resume normally
        vm.resume(state, eventRef);

        log.debug("resume ['{}'] -> done", eventRef);

        return ProcessSnapshot.builder()
                .from(snapshot)
                .vmState(state)
                .build();
    }

    private RuntimeFactory createRuntimeFactory(ProcessDefinition processDefinition, GlobalVariables globalVariables) {
        Map<Class<?>, Object> m = new HashMap<>();

        // collect all "services" that we might need in runtime
        m.put(Compiler.class, compiler);
        m.put(ContextFactory.class, contextFactory);
        m.put(TaskProviders.class, taskProviders);
        m.put(ExpressionEvaluator.class, expressionEvaluator);
        m.put(CheckpointService.class, checkpointService);
        m.put(ProcessDefinition.class, processDefinition);
        m.put(GlobalVariables.class, globalVariables);
        m.put(FormService.class, formService);
        m.put(SynchronizationService.class, synchronizationService);

        Map<Class<?>, ?> services = Collections.unmodifiableMap(m);
        return vm -> new DefaultRuntime(vm, services);
    }

    private VM createVM(ProcessDefinition processDefinition, GlobalVariables globalVariables) {
        Collection<ExecutionListener> listeners = new ArrayList<>();
        listeners.add(new SynchronizationServiceListener(synchronizationService));
        listeners.addAll(this.listeners);
        return new VM(createRuntimeFactory(processDefinition, globalVariables), listeners);
    }

    public static class Builder {

        private Injector injector;

        private Path workDir;
        private UUID instanceId;

        private Compiler compiler;
        private ContextFactory contextFactory;
        private TaskProviders taskProviders;
        private ExpressionEvaluator expressionEvaluator;
        private CheckpointService checkpointService;
        private FormService formService;
        private SynchronizationService synchronizationService;

        private ProcessStatusCallback statusCallback;
        private Collection<ExecutionListener> listeners;

        public Builder injector(Injector injector) {
            this.injector = injector;
            return this;
        }

        public Builder workDir(Path workDir) {
            this.workDir = workDir;
            return this;
        }

        public Builder instanceId(UUID instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder compiler(Compiler compiler) {
            this.compiler = compiler;
            return this;
        }

        public Builder contextFactory(ContextFactory contextFactory) {
            this.contextFactory = contextFactory;
            return this;
        }

        public Builder taskProvider(TaskProvider taskProvider) {
            if (this.taskProviders == null) {
                this.taskProviders = new TaskProviders();
            }

            this.taskProviders.register(taskProvider);
            return this;
        }

        public Builder taskProviders(TaskProviders taskProviders) {
            this.taskProviders = taskProviders;
            return this;
        }

        public Builder expressionEvaluator(ExpressionEvaluator expressionEvaluator) {
            this.expressionEvaluator = expressionEvaluator;
            return this;
        }

        public Builder snapshotService(CheckpointService checkpointService) {
            this.checkpointService = checkpointService;
            return this;
        }

        public Builder formService(FormService formService) {
            this.formService = formService;
            return this;
        }

        public Builder synchronizationService(SynchronizationService synchronizationService) {
            this.synchronizationService = synchronizationService;
            return this;
        }

        public Builder statusCallback(ProcessStatusCallback statusCallback) {
            this.statusCallback = statusCallback;
            return this;
        }

        public Builder listeners(Collection<ExecutionListener> listeners) {
            this.listeners = new ArrayList<>(listeners);
            return this;
        }

        public Builder listener(ExecutionListener listener) {
            if (this.listeners == null) {
                this.listeners = new ArrayList<>();
            }
            this.listeners.add(listener);
            return this;
        }

        public Runner build() {
            if (workDir == null) {
                workDir = inject(WorkingDirectory.class, "workDir").getValue();
            }

            if (instanceId == null) {
                instanceId = inject(InstanceId.class, "instanceId").getValue();
            }

            if (compiler == null) {
                compiler = inject(Compiler.class, "compiler");
            }

            if (contextFactory == null) {
                contextFactory = inject(ContextFactory.class, "contextFactory");
            }

            if (taskProviders == null) {
                taskProviders = inject(TaskProviders.class, "taskProviders");
            }

            if (expressionEvaluator == null) {
                expressionEvaluator = inject(ExpressionEvaluator.class, "expressionEvaluator");
            }

            if (checkpointService == null) {
                checkpointService = inject(CheckpointService.class, "snapshotService");
            }

            if (formService == null) {
                // TODO this should be implementation details hidden in the service itself
                Path attachmentsDir = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
                Path stateDir = attachmentsDir.resolve(Constants.Files.JOB_STATE_DIR_NAME);
                Path formsDir = stateDir.resolve(Constants.Files.JOB_FORMS_V2_DIR_NAME);

                formService = new FormService(formsDir);
            }

            if (synchronizationService == null) {
                synchronizationService = inject(SynchronizationService.class, "synchronizationService");
            }

            if (statusCallback == null) {
                statusCallback = inject(ProcessStatusCallback.class, "statusCallback");
            }

            if (listeners == null) {
                listeners = Collections.emptyList();
            }

            return new Runner(this);
        }

        private <T> T inject(Class<T> type, String property) {
            if (injector != null) {
                return injector.getInstance(type);
            } else {
                throw new IllegalStateException("'" + property + "' must be configured");
            }
        }
    }
}
