package com.walmartlabs.concord.cli;

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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.cli.runner.CliCheckpointService;
import com.walmartlabs.concord.cli.runner.CliDockerService;
import com.walmartlabs.concord.cli.runner.CliSecretService;
import com.walmartlabs.concord.runtime.v2.runner.DefaultSynchronizationService;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskV2Provider;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.sdk.DockerService;
import com.walmartlabs.concord.sdk.SecretService;
import com.walmartlabs.concord.runtime.v2.runner.SynchronizationService;

import java.nio.file.Path;

public class CliServicesModule extends AbstractModule {

    private final Path secretStoreDir;

    public CliServicesModule(Path secretStoreDir) {
        this.secretStoreDir = secretStoreDir;
    }

    @Override
    protected void configure() {
        bind(SecretService.class).toInstance(new CliSecretService(secretStoreDir));
        bind(CheckpointService.class).to(CliCheckpointService.class);
        bind(DockerService.class).to(CliDockerService.class);
        bind(SynchronizationService.class).to(DefaultSynchronizationService.class);

        Multibinder<TaskProvider> taskProviders = Multibinder.newSetBinder(binder(), TaskProvider.class);
        taskProviders.addBinding().to(TaskV2Provider.class);
    }
}
