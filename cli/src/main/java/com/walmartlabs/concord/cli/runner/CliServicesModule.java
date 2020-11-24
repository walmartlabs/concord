package com.walmartlabs.concord.cli.runner;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.runtime.v2.runner.*;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.guice.BaseRunnerModule;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.runner.logging.SimpleLogger;
import com.walmartlabs.concord.runtime.v2.sdk.DependencyManager;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.runtime.v2.sdk.LockService;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.svm.ExecutionListener;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.Map;

public class CliServicesModule extends AbstractModule {

    private final Path secretStoreDir;
    private final Path workDir;
    private final VaultProvider vaultProvider;

    public CliServicesModule(Path secretStoreDir, Path workDir, VaultProvider vaultProvider) {
        this.secretStoreDir = secretStoreDir;
        this.workDir = workDir;
        this.vaultProvider = vaultProvider;
    }

    @Override
    protected void configure() {
        install(new BaseRunnerModule());

        bind(RunnerLogger.class).to(SimpleLogger.class);
        bind(SecretService.class).toInstance(new CliSecretService(workDir, secretStoreDir, vaultProvider));
        bind(DockerService.class).to(CliDockerService.class);

        bind(CheckpointService.class).to(CliCheckpointService.class);
        bind(PersistenceService.class).to(DefaultPersistenceService.class);
        bind(ProcessStatusCallback.class).toInstance(instanceId -> {
        });

        bind(DefaultTaskVariablesService.class).toInstance(new MapBackedDefaultTaskVariablesService(readDefaultVars()));

        bind(LockService.class).to(CliLockService.class);

        bind(DependencyManager.class).to(DefaultDependencyManager.class).in(Singleton.class);

        Multibinder.newSetBinder(binder(), ExecutionListener.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> readDefaultVars() {
        try {
            return new ObjectMapper().readValue(CliServicesModule.class.getResourceAsStream("/default-vars.json"), Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
