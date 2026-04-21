package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.TaskHolder;
import com.walmartlabs.concord.runtime.v2.runner.DefaultTaskVariablesService;
import com.walmartlabs.concord.runtime.v2.runner.InjectorFactory;
import com.walmartlabs.concord.runtime.v2.runner.guice.ProcessDependenciesModule;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskInjectCompatibilityTest {

    @TempDir
    private Path tempDir;

    @Test
    void supportsLegacyAndJakartaTaskJarsInSameRuntime() throws Exception {
        var legacyJar = fixtureJar("legacy-task.jar",
                Map.of(
                        "compat.LegacyTask", legacyTask(),
                        "compat.LegacySingletonTask", legacySingletonTask()),
                Map.of("META-INF/sisu/javax.inject.Named", List.of(
                        "compat.LegacyTask",
                        "compat.LegacySingletonTask")));

        var jakartaJar = fixtureJar("jakarta-task.jar",
                Map.of(
                        "compat.JakartaConstructorTask", jakartaConstructorTask(),
                        "compat.JakartaFieldTask", jakartaFieldTask(),
                        "compat.JakartaMethodTask", jakartaMethodTask(),
                        "compat.JakartaSingletonTask", jakartaSingletonTask()),
                Map.of("META-INF/sisu/jakarta.inject.Named", List.of(
                        "compat.JakartaConstructorTask",
                        "compat.JakartaFieldTask",
                        "compat.JakartaMethodTask",
                        "compat.JakartaSingletonTask")));

        var runnerCfg = RunnerConfiguration.builder()
                .api(ApiConfiguration.builder().build())
                .dependencies(List.of(legacyJar.toString(), jakartaJar.toString()))
                .build();

        var processCfg = ProcessConfiguration.builder().build();
        var injector = new InjectorFactory(new WorkingDirectory(tempDir),
                runnerCfg,
                () -> processCfg,
                testServices(),
                new ProcessDependenciesModule(tempDir, runnerCfg.dependencies(), runnerCfg.debug()))
                .create();

        var classLoader = injector.getInstance(Key.get(ClassLoader.class, Names.named("runtime")));
        assertNotNull(Class.forName("javax.inject.Inject", false, classLoader));
        assertNotNull(Class.forName("jakarta.inject.Inject", false, classLoader));

        var holder = injector.getInstance(Key.get(new com.google.inject.TypeLiteral<TaskHolder<Task>>() {
        }));
        assertTrue(holder.keys().contains("legacyTask"));
        assertTrue(holder.keys().contains("jakartaConstructorTask"));
        assertTrue(holder.keys().contains("jakartaFieldTask"));
        assertTrue(holder.keys().contains("jakartaMethodTask"));
        assertFalse(holder.keys().contains("legacySingletonTask"));
        assertFalse(holder.keys().contains("jakartaSingletonTask"));

        var ctx = mock(Context.class);
        when(ctx.processConfiguration()).thenReturn(processCfg);

        var provider = injector.getInstance(TaskV2Provider.class);
        assertTask(provider, ctx, "legacyTask");
        assertTask(provider, ctx, "jakartaConstructorTask");
        assertTask(provider, ctx, "jakartaFieldTask");
        assertTask(provider, ctx, "jakartaMethodTask");
    }

    private static AbstractModule testServices() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Context.class).toProvider(ContextProvider.class);
                bind(DefaultTaskVariablesService.class).toInstance(new DefaultTaskVariablesService() {
                });
            }
        };
    }

    private static void assertTask(TaskV2Provider provider, Context ctx, String name) throws Exception {
        var task = provider.createTask(ctx, name);
        assertNotNull(task);

        var result = (TaskResult.SimpleResult) task.execute(null);
        assertEquals(Boolean.TRUE, result.values().get("ctx"));
    }

    private Path fixtureJar(String name, Map<String, String> sources, Map<String, List<String>> resources) throws IOException {
        var srcDir = Files.createDirectories(tempDir.resolve(name + "-src"));
        var classesDir = Files.createDirectories(tempDir.resolve(name + "-classes"));
        var sourceFiles = new java.util.ArrayList<Path>();

        for (var entry : sources.entrySet()) {
            var sourceFile = srcDir.resolve(entry.getKey().replace('.', File.separatorChar) + ".java");
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, entry.getValue(), StandardCharsets.UTF_8);
            sourceFiles.add(sourceFile);
        }

        compile(sourceFiles, classesDir);

        var jarPath = tempDir.resolve(name);
        try (var out = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addClasses(out, classesDir);
            addResources(out, resources);
        }
        return jarPath;
    }

    private static void compile(List<Path> sourceFiles, Path classesDir) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");

        try (var fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            var options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classesDir.toString());

            assertTrue(compiler.getTask(null, fileManager, null, options, null, units).call());
        }
    }

    private static void addClasses(JarOutputStream out, Path classesDir) throws IOException {
        try (var stream = Files.walk(classesDir)) {
            var files = stream
                    .filter(Files::isRegularFile)
                    .toList();

            for (var file : files) {
                var entryName = classesDir.relativize(file).toString().replace(File.separatorChar, '/');
                if (entryName.startsWith("META-INF/sisu/")) {
                    continue;
                }

                out.putNextEntry(new JarEntry(entryName));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
    }

    private static void addResources(JarOutputStream out, Map<String, List<String>> resources) throws IOException {
        for (var resource : resources.entrySet()) {
            out.putNextEntry(new JarEntry(resource.getKey()));
            out.write(String.join("\n", resource.getValue()).getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.closeEntry();
        }
    }

    private static String legacyTask() {
        return """
                package compat;

                import com.walmartlabs.concord.runtime.v2.sdk.Context;
                import com.walmartlabs.concord.runtime.v2.sdk.Task;
                import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
                import com.walmartlabs.concord.runtime.v2.sdk.Variables;

                @javax.inject.Named("legacyTask")
                public class LegacyTask implements Task {

                    private final Context ctx;

                    @javax.inject.Inject
                    public LegacyTask(Context ctx) {
                        this.ctx = ctx;
                    }

                    @Override
                    public TaskResult execute(Variables input) {
                        return TaskResult.success().value("ctx", ctx != null);
                    }
                }
                """;
    }

    private static String legacySingletonTask() {
        return """
                package compat;

                import com.walmartlabs.concord.runtime.v2.sdk.Task;

                @javax.inject.Named("legacySingletonTask")
                @javax.inject.Singleton
                public class LegacySingletonTask implements Task {
                }
                """;
    }

    private static String jakartaConstructorTask() {
        return """
                package compat;

                import com.walmartlabs.concord.runtime.v2.sdk.Context;
                import com.walmartlabs.concord.runtime.v2.sdk.Task;
                import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
                import com.walmartlabs.concord.runtime.v2.sdk.Variables;

                @jakarta.inject.Named("jakartaConstructorTask")
                public class JakartaConstructorTask implements Task {

                    private final Context ctx;

                    @jakarta.inject.Inject
                    public JakartaConstructorTask(Context ctx) {
                        this.ctx = ctx;
                    }

                    @Override
                    public TaskResult execute(Variables input) {
                        return TaskResult.success().value("ctx", ctx != null);
                    }
                }
                """;
    }

    private static String jakartaFieldTask() {
        return """
                package compat;

                import com.walmartlabs.concord.runtime.v2.sdk.Context;
                import com.walmartlabs.concord.runtime.v2.sdk.Task;
                import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
                import com.walmartlabs.concord.runtime.v2.sdk.Variables;

                @jakarta.inject.Named("jakartaFieldTask")
                public class JakartaFieldTask implements Task {

                    @jakarta.inject.Inject
                    private Context ctx;

                    @Override
                    public TaskResult execute(Variables input) {
                        return TaskResult.success().value("ctx", ctx != null);
                    }
                }
                """;
    }

    private static String jakartaMethodTask() {
        return """
                package compat;

                import com.walmartlabs.concord.runtime.v2.sdk.Context;
                import com.walmartlabs.concord.runtime.v2.sdk.Task;
                import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
                import com.walmartlabs.concord.runtime.v2.sdk.Variables;

                @jakarta.inject.Named("jakartaMethodTask")
                public class JakartaMethodTask implements Task {

                    private Context ctx;

                    @jakarta.inject.Inject
                    public void setContext(Context ctx) {
                        this.ctx = ctx;
                    }

                    @Override
                    public TaskResult execute(Variables input) {
                        return TaskResult.success().value("ctx", ctx != null);
                    }
                }
                """;
    }

    private static String jakartaSingletonTask() {
        return """
                package compat;

                import com.walmartlabs.concord.runtime.v2.sdk.Task;

                @jakarta.inject.Named("jakartaSingletonTask")
                @jakarta.inject.Singleton
                public class JakartaSingletonTask implements Task {
                }
                """;
    }
}
