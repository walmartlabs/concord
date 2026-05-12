package com.walmartlabs.concord.cli;

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

import com.google.inject.Injector;
import com.walmartlabs.concord.cli.CliConfig.CliConfigContext;
import com.walmartlabs.concord.cli.runner.CliServicesModule;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.dependencymanager.DependencyManagerConfiguration;
import com.walmartlabs.concord.dependencymanager.DependencyManagerRepositories;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.runner.InjectorFactory;
import com.walmartlabs.concord.runtime.v2.runner.guice.ProcessDependenciesModule;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class LocalCliRuntime {

    static DependencyManager createDependencyManager(Path depsCacheDir) throws IOException {
        var cfgFile = Paths.get(System.getProperty("user.home"), ".concord", "mvn.json");
        if (Files.exists(cfgFile)) {
            return new DependencyManager(DependencyManagerConfiguration.of(depsCacheDir, DependencyManagerRepositories.get(cfgFile)));
        }
        return new DependencyManager(DependencyManagerConfiguration.of(depsCacheDir));
    }

    static Injector createInjector(Path workDir,
                                   RunnerConfiguration runnerCfg,
                                   ProcessConfiguration processCfg,
                                   CliConfigContext cliConfigContext,
                                   Path defaultTaskVars,
                                   DependencyManager dependencyManager,
                                   Verbosity verbosity) {

        return new InjectorFactory(new WorkingDirectory(workDir),
                runnerCfg,
                () -> processCfg,
                new ProcessDependenciesModule(workDir, runnerCfg.dependencies(), processCfg.debug()),
                new CliServicesModule(cliConfigContext, workDir, defaultTaskVars, dependencyManager, verbosity))
                .create();
    }

    static void notifyProjectLoaded(Path workDir) throws Exception {
        var loader = new ProjectLoaderV2(new NoopImportManager());
        loader.load(workDir, new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER);
    }

    private LocalCliRuntime() {
    }
}
