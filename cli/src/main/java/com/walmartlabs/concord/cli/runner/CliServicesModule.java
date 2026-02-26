package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.cli.CliConfig.CliConfigContext;
import com.walmartlabs.concord.cli.Verbosity;
import com.walmartlabs.concord.cli.secrets.CliSecretService;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.runtime.v2.runner.*;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.guice.BaseRunnerModule;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.runner.logging.SimpleLogger;
import com.walmartlabs.concord.runtime.v2.runner.remote.ApiClientProvider;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.runtime.v2.sdk.LockService;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.svm.ExecutionListener;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

public class CliServicesModule extends AbstractModule {

    private final CliConfigContext cliConfigContext;
    private final Path workDir;
    private final Path defaultTaskVars;
    private final DependencyManager dependencyManager;
    private final Verbosity verbosity;

    public CliServicesModule(CliConfigContext cliConfigContext,
                             Path workDir,
                             Path defaultTaskVars,
                             DependencyManager dependencyManager,
                             Verbosity verbosity) {

        this.cliConfigContext = cliConfigContext;
        this.workDir = workDir;
        this.defaultTaskVars = defaultTaskVars;
        this.dependencyManager = dependencyManager;
        this.verbosity = verbosity;
    }

    @Override
    protected void configure() {
        install(new BaseRunnerModule());

        bind(RunnerLogger.class).to(SimpleLogger.class);

        bind(SecretService.class).toInstance(CliSecretService.create(cliConfigContext, workDir, verbosity));

        bind(DockerService.class).to(CliDockerService.class);

        bind(CheckpointService.class).to(CliCheckpointService.class);
        bind(PersistenceService.class).to(DefaultPersistenceService.class);
        bind(ProcessStatusCallback.class).toInstance(instanceId -> {
        });

        bind(ApiKey.class).toInstance(ApiKey.create(cliConfigContext, workDir, verbosity));
        bind(ApiClient.class).toProvider(CliApiClientProvider.class);

        bind(DefaultTaskVariablesService.class)
                .toInstance(new MapBackedDefaultTaskVariablesService(readDefaultVars(defaultTaskVars)));

        bind(LockService.class).to(CliLockService.class);

        bind(DependencyManager.class).toInstance(dependencyManager);
        bind(com.walmartlabs.concord.runtime.v2.sdk.DependencyManager.class).to(DefaultDependencyManager.class).in(Singleton.class);

        Multibinder<ExecutionListener> executionListeners = Multibinder.newSetBinder(binder(), ExecutionListener.class);
        if (verbosity.logFlowSteps()) {
            executionListeners.addBinding().to(FlowStepLogger.class);
        }

        if (verbosity.logTaskParams()) {
            Multibinder<TaskCallListener> taskCallListeners = Multibinder.newSetBinder(binder(), TaskCallListener.class);
            taskCallListeners.addBinding().toInstance(new TaskParamsLogger());
        }
    }

    private static Map<String, Map<String, Object>> readDefaultVars(Path defaultTaskVars) {
        if (Files.exists(defaultTaskVars)) {
            try (InputStream is = Files.newInputStream(defaultTaskVars)) {
                return parseDefaultVars(() -> is);
            } catch (Exception e) {
                System.out.println("Error parsing default variables in '" + defaultTaskVars + "': " + e.getMessage());
            }
        }

        return parseDefaultVars(() -> CliServicesModule.class.getResourceAsStream("/default-vars.json"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> parseDefaultVars(Supplier<InputStream> isSupplier) {
        try (InputStream is = isSupplier.get()) {
            if (is == null) {
                throw new IllegalStateException("Default variables input stream is null.");
            }

            return new ObjectMapper().readValue(is, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
