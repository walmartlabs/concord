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

import com.google.inject.Key;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
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
import com.walmartlabs.concord.runtime.v25.runner.engine.LifecycleEvent;
import com.walmartlabs.concord.runtime.v25.runner.engine.ProcessResult;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.CustomBeanMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.runtime.v2.sdk.ELFunction;
import com.walmartlabs.concord.runtime.v2.sdk.FileService;
import com.walmartlabs.concord.runtime.v2.sdk.LockService;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
    static ProcessResult runV25(Injector injector, Path workDir, RunnerConfiguration runnerConfiguration,
                                ProcessConfiguration processConfiguration, Verbosity verbosity) throws Exception {
        var providers = injector.getInstance(TaskProviders.class);
        var adapter = new TaskProvider() {
            @Override
            public Task createTask(Context context, String key) {
                return providers.createTask(context, key);
            }

            @Override
            public Class<? extends Task> getTaskClass(Context context, String key) {
                return providers.getTaskClass(context, key);
            }

            @Override
            public boolean hasTask(String key) {
                return providers.hasTask(key);
            }

            @Override
            public java.util.Set<String> names() {
                return providers.names();
            }
        };
        var taskMethodResolvers = injector.getInstance(
                Key.get(new TypeLiteral<Set<CustomTaskMethodResolver>>() { }));
        var beanMethodResolvers = injector.getInstance(
                Key.get(new TypeLiteral<Set<CustomBeanMethodResolver>>() { }));
        var services = new com.walmartlabs.concord.runtime.v25.runner.Main.LocalServices(
                injector.getInstance(DockerService.class), injector.getInstance(SecretService.class),
                injector.getInstance(LockService.class), injector.getInstance(FileService.class));
        var callbacks = new com.walmartlabs.concord.runtime.v25.runner.Main.LocalCallbacks(
                lifecycleListener(verbosity), taskHooks(verbosity));
        return com.walmartlabs.concord.runtime.v25.runner.Main.executeLocal(workDir, runnerConfiguration,
                processConfiguration, adapter, injector.getInstance(SensitiveDataHolder.class), services,
                List.copyOf(taskMethodResolvers), List.copyOf(beanMethodResolvers), discoverFunctions(injector), callbacks);
    }

    private static Map<String, Method> discoverFunctions(Injector injector) {
        var classes = new LinkedHashSet<Class<?>>();
        injector.getAllBindings().values().forEach(binding -> classes.add(binding.getKey().getTypeLiteral().getRawType()));
        // Sisu-indexed services are already bound by InjectorFactory's WireModule/SpaceModule wiring,
        // so walking all bindings covers annotated @ELFunction classes from dependencies.

        var result = new LinkedHashMap<String, Method>();
        for (var clazz : classes) {
            for (var method : clazz.getDeclaredMethods()) {
                var annotation = method.getAnnotation(ELFunction.class);
                if (annotation == null) {
                    continue;
                }
                if (method.getDeclaringClass().getPackageName()
                        .startsWith("com.walmartlabs.concord.runtime.v2.runner.el.functions")) {
                    continue;
                }
                if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
                    throw new IllegalArgumentException("@ELFunction method must be public and static: "
                            + clazz.getName() + "." + method.getName());
                }
                var name = annotation.value().isBlank() ? method.getName() : annotation.value();
                var previous = result.putIfAbsent(name, method);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate @ELFunction '" + name + "': "
                            + previous.getDeclaringClass().getName() + "." + previous.getName() + " and "
                            + clazz.getName() + "." + method.getName());
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Consumer<LifecycleEvent> lifecycleListener(Verbosity verbosity) {
        if (!verbosity.logFlowSteps()) {
            return event -> { };
        }
        return event -> {
            if (event.type() == LifecycleEvent.Type.STEP_STARTED) {
                System.out.println(">>> '" + event.data().getOrDefault("description", event.path()) + "' @ "
                        + event.source() + ":" + event.line());
            }
        };
    }

    private static List<TaskRuntime.TaskHook> taskHooks(Verbosity verbosity) {
        if (!verbosity.logTaskParams()) {
            return List.of();
        }
        return List.of(new TaskRuntime.TaskHook() {
            private final Map<TaskRuntime.Invocation, Long> started = new ConcurrentHashMap<>();

            @Override
            public void before(TaskRuntime.Invocation invocation) {
                started.put(invocation, System.nanoTime());
                System.out.println("     in: " + invocation.arguments());
            }

            @Override
            public void after(TaskRuntime.Invocation invocation, Object result, Throwable failure) {
                var start = started.remove(invocation);
                System.out.println("     out: " + result);
                if (start != null) {
                    System.out.println("     duration: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms");
                }
                if (failure != null) {
                    System.out.println("    error: " + failure);
                }
            }
        });
    }

    static void notifyProjectLoaded(Path workDir) throws Exception {
        var loader = new ProjectLoaderV2(new NoopImportManager());
        loader.load(workDir, new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER);
    }

    private LocalCliRuntime() {
    }
}
