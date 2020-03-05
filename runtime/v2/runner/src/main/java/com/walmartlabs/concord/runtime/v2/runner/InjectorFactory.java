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
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskHolder;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.sdk.Constants;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InjectorFactory {

    private static final Logger log = LoggerFactory.getLogger(InjectorFactory.class);

    private final ClassLoader parentClassLoader;
    private final WorkingDirectory workDir;
    private final RunnerConfiguration runnerCfg;

    public InjectorFactory(ClassLoader parentClassLoader,
                           WorkingDirectory workDir,
                           RunnerConfiguration runnerCfg) {

        this.parentClassLoader = parentClassLoader;
        this.workDir = workDir;
        this.runnerCfg = runnerCfg;
    }

    public Injector create() throws IOException {
        Collection<String> dependencies = runnerCfg.dependencies();
        ClassLoader dependenciesClassLoader = loadDependencies(dependencies);

        Module tasks = new AbstractModule() {
            @Override
            protected void configure() {
                TaskHolder<com.walmartlabs.concord.sdk.Task> v1Holder = new TaskHolder<>();
                bindListener(new SubClassesOf(com.walmartlabs.concord.sdk.Task.class), new TaskClassesListener<>(v1Holder));

                TaskHolder<Task> v2Holder = new TaskHolder<>();
                bindListener(new SubClassesOf(Task.class), new TaskClassesListener<>(v2Holder));

                bind(new TypeLiteral<TaskHolder<com.walmartlabs.concord.sdk.Task>>() {
                }).annotatedWith(TaskHolder.V1.class).toInstance(v1Holder);

                bind(new TypeLiteral<TaskHolder<Task>>() {
                }).annotatedWith(TaskHolder.V2.class).toInstance(v2Holder);
            }
        };

        Module m = new WireModule(
                new ConfigurationModule(workDir, runnerCfg),
                tasks,
                new SpaceModule(new URLClassSpace(parentClassLoader)),
                new SpaceModule(new URLClassSpace(dependenciesClassLoader)));

        return Guice.createInjector(m);
    }

    private URLClassLoader loadDependencies(Collection<String> dependencies) throws IOException {
        List<URL> urls = toURLs(dependencies);
        return new URLClassLoader(urls.toArray(new URL[0]), InjectorFactory.class.getClassLoader());
    }

    private List<URL> toURLs(Collection<String> dependencies) throws IOException {
        List<URL> urls = dependencies.stream()
                .sorted()
                .map(s -> {
                    try {
                        // assume all dependencies are resolved into file paths at this point
                        return new URL("file://" + s);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException("Invalid dependency " + s + ": " + e.getMessage());
                    }
                }).collect(Collectors.toList());

        // collect all JARs from the `${workDir}/lib/` directory

        Path lib = workDir.getPath().resolve(Constants.Files.LIBRARIES_DIR_NAME);
        if (Files.exists(lib)) {
            try (Stream<Path> s = Files.list(lib).sorted()) {
                s.forEach(f -> {
                    if (f.toString().endsWith(".jar")) {
                        try {
                            urls.add(f.toUri().toURL());
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        }

        return urls;
    }

    private static class TaskClassesListener<T> implements TypeListener {

        private final TaskHolder<T> holder;

        public TaskClassesListener(TaskHolder<T> holder) {
            this.holder = holder;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I> void hear(TypeLiteral<I> typeLiteral, TypeEncounter<I> typeEncounter) {
            Class<T> klass = (Class<T>) typeLiteral.getRawType();

            Named n = klass.getAnnotation(Named.class);
            if (n == null) {
                log.warn("Task class without @Named: {}", klass);
                return;
            }

            String key = n.value();
            if ("".equals(key)) {
                log.warn("Task class without a @Named value: {}", klass);
                return;
            }

            holder.add(key, klass);
        }
    }

    private static class SubClassesOf extends AbstractMatcher<TypeLiteral<?>> {

        private final Class<?> baseClass;

        private SubClassesOf(Class<?> baseClass) {
            this.baseClass = baseClass;
        }

        @Override
        public boolean matches(TypeLiteral<?> t) {
            return baseClass.isAssignableFrom(t.getRawType());
        }
    }

    private static class ConfigurationModule extends AbstractModule {

        private final WorkingDirectory workDir;
        private final RunnerConfiguration cfg;

        private ConfigurationModule(WorkingDirectory workDir, RunnerConfiguration cfg) {
            this.workDir = workDir;
            this.cfg = cfg;
        }

        @Override
        protected void configure() {
            bind(WorkingDirectory.class).toInstance(workDir);
            bind(RunnerConfiguration.class).toInstance(cfg);
        }
    }
}
