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

import com.google.inject.*;
import com.google.inject.Module;
import com.walmartlabs.concord.common.SystemTimeProvider;
import com.walmartlabs.concord.common.TimeProvider;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.InjectorUtils;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.common.injector.TaskHolder;
import com.walmartlabs.concord.runtime.v2.runner.guice.CurrentClasspathModule;
import com.walmartlabs.concord.runtime.v2.runner.guice.DefaultRunnerModule;
import com.walmartlabs.concord.runtime.v2.runner.guice.ProcessDependenciesModule;
import com.walmartlabs.concord.runtime.v2.runner.logging.CustomLayout;
import com.walmartlabs.concord.runtime.v2.runner.tasks.V2;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import org.eclipse.sisu.wire.WireModule;

import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// TODO poor ergonomics
public class InjectorFactory {

    public static Injector createDefault(RunnerConfiguration runnerCfg) {
        Path src = Paths.get(System.getProperty("user.dir"));

        TimeProvider timeProvider = new SystemTimeProvider();

        Provider<ProcessConfiguration> processCfgProvider = new DefaultProcessConfigurationProvider(src, timeProvider);
        WorkingDirectory workDir = new WorkingDirectory(src);

        return new InjectorFactory(workDir,
                runnerCfg,
                processCfgProvider,
                new DefaultRunnerModule(), // bind default services
                new ProcessDependenciesModule(workDir.getValue(), runnerCfg.dependencies(), runnerCfg.debug()), // grab process dependencies,
                new TimeProviderModule(timeProvider)) // use regular clock
                .create();
    }

    private final WorkingDirectory workDir;
    private final RunnerConfiguration runnerCfg;
    private final com.google.inject.Module[] modules;
    private final Provider<ProcessConfiguration> processConfigurationProvider;

    public InjectorFactory(WorkingDirectory workDir,
                           RunnerConfiguration runnerCfg,
                           Provider<ProcessConfiguration> processConfigurationProvider,
                           com.google.inject.Module... modules) {

        this.workDir = workDir;
        this.runnerCfg = runnerCfg;
        this.modules = modules;
        this.processConfigurationProvider = processConfigurationProvider;
    }

    public Injector create() {
        List<Module> l = new ArrayList<>();
        l.add(new ConfigurationModule(workDir, runnerCfg, processConfigurationProvider));
        l.add(new CurrentClasspathModule());

        com.google.inject.Module tasks = new AbstractModule() {
            @Override
            protected void configure() {
                TaskHolder<Task> holder = new TaskHolder<>();
                bindListener(InjectorUtils.subClassesOf(Task.class), InjectorUtils.taskClassesListener(holder));

                bind(new TypeLiteral<TaskHolder<Task>>() {
                }).annotatedWith(V2.class).toInstance(holder);
            }
        };
        l.add(tasks);

        if (modules != null) {
            l.addAll(Arrays.asList(modules));
        }

        com.google.inject.Module m = new WireModule(l);
        return Guice.createInjector(m);
    }

    private static class ConfigurationModule extends AbstractModule {

        private final WorkingDirectory workDir;
        private final RunnerConfiguration runnerCfg;
        private final Provider<ProcessConfiguration> processCfgProvider;

        private ConfigurationModule(WorkingDirectory workDir,
                                    RunnerConfiguration runnerCfg,
                                    Provider<ProcessConfiguration> processCfgProvider) {

            this.workDir = workDir;
            this.runnerCfg = runnerCfg;
            this.processCfgProvider = processCfgProvider;
        }

        @Override
        protected void configure() {
            bind(WorkingDirectory.class).toInstance(workDir);
            if (runnerCfg.logging().workDirMasking()) {
                CustomLayout.enableWorkingDirectoryMasking(workDir);
            }

            bind(RunnerConfiguration.class).toInstance(runnerCfg);
            bind(ProcessConfiguration.class).toProvider(processCfgProvider);
            bind(InstanceId.class).toProvider(InstanceIdProvider.class);
        }
    }

    private static class InstanceIdProvider implements Provider<InstanceId> {

        private final UUID value;

        @Inject
        private InstanceIdProvider(ProcessConfiguration cfg) {
            this.value = cfg.instanceId();
        }

        @Override
        public InstanceId get() {
            return new InstanceId(value);
        }
    }
}
