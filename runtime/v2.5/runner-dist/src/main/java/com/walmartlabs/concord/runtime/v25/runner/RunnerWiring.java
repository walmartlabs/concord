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
import com.walmartlabs.concord.common.ReflectionUtils;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.client2.DefaultApiClientFactory;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.TaskHolder;
import com.walmartlabs.concord.runtime.v2.sdk.ApiConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.CustomBeanMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.runtime.v2.sdk.ELFunction;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;
import org.eclipse.sisu.BeanEntry;
import org.eclipse.sisu.inject.BeanLocator;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime-v2.5's self-contained Guice/Sisu bootstrap and task discovery. */
final class RunnerWiring {

    private static final Logger log = LoggerFactory.getLogger(RunnerWiring.class);

    static Runtime create(RunnerConfiguration runnerConfiguration) {
        var workDirectory = new WorkingDirectory(Path.of(System.getProperty("user.dir")));
        var processConfiguration = loadProcessConfiguration(workDirectory.getValue());
        var apiClient = apiClient(runnerConfiguration, processConfiguration);
        var apiConfiguration = new SdkApiConfiguration(runnerConfiguration);
        var apiClientFactory = apiClientFactory(apiConfiguration, processConfiguration.instanceId());
        var contexts = new ContextScope();
        var tasks = new TaskHolder<Task>();
        var sensitiveData = new SensitiveDataRegistry();
        var persistenceService = new PersistenceService(workDirectory.getValue(), objectMapper());
        var docker = RunnerServices.docker(workDirectory.getValue(), runnerConfiguration, processConfiguration.instanceId());
        var locks = RunnerServices.locks(apiClient, processConfiguration.instanceId());
        var secrets = RunnerServices.secrets(runnerConfiguration, apiClient, processConfiguration.instanceId(),
                workDirectory.getValue());
        var files = RunnerServices.files(workDirectory.getValue());
        var dependencies = dependencyClassLoader(workDirectory.getValue(), runnerConfiguration.dependencies());
        var module = new AbstractModule() {
            @Override
            protected void configure() {
                bind(WorkingDirectory.class).toInstance(workDirectory);
                bind(RunnerConfiguration.class).toInstance(runnerConfiguration);
                bind(ProcessConfiguration.class).toInstance(processConfiguration);
                bind(ApiClient.class).toInstance(apiClient);
                bind(ApiClientFactory.class).toInstance(apiClientFactory);
                bind(ObjectMapper.class).toInstance(objectMapper());
                bind(ApiConfiguration.class).toInstance(apiConfiguration);
                bind(SensitiveDataHolder.class).toInstance(sensitiveData);
                bind(ContextScope.class).toInstance(contexts);
                bind(Context.class).toProvider(contexts);
                bind(com.walmartlabs.concord.runtime.v2.sdk.DockerService.class).toInstance(docker);
                bind(com.walmartlabs.concord.runtime.v2.sdk.LockService.class).toInstance(locks);
                bind(com.walmartlabs.concord.runtime.v2.sdk.SecretService.class).toInstance(secrets);
                bind(com.walmartlabs.concord.runtime.v2.sdk.FileService.class).toInstance(files);
                bind(new TypeLiteral<TaskHolder<Task>>() { }).toInstance(tasks);
                var taskProviders = Multibinder.newSetBinder(binder(), TaskProvider.class);
                taskProviders.addBinding().to(DiscoveredTasks.class);
                bind(new TypeLiteral<List<TaskProvider>>() { }).toProvider(TaskProvidersProvider.class);
                Multibinder.newSetBinder(binder(), CustomTaskMethodResolver.class);
                Multibinder.newSetBinder(binder(), CustomBeanMethodResolver.class);
            }
        };

        var modules = new ArrayList<com.google.inject.Module>();
        modules.add(new SpaceModule(new URLClassSpace(RunnerWiring.class.getClassLoader()), BeanScanning.GLOBAL_INDEX));
        modules.add(module);
        if (dependencies != null) {
            Thread.currentThread().setContextClassLoader(dependencies);
            modules.add(new SpaceModule(new URLClassSpace(dependencies), BeanScanning.GLOBAL_INDEX));
        }
        Injector injector = Guice.createInjector(new WireModule(modules));
        discoverTasks(injector, tasks);
        var taskProviders = List.copyOf(
                injector.getInstance(Key.get(new TypeLiteral<Set<TaskProvider>>() { })));
        var taskMethodResolvers = List.copyOf(
                injector.getInstance(Key.get(new TypeLiteral<Set<CustomTaskMethodResolver>>() { })));
        var beanMethodResolvers = List.copyOf(
                injector.getInstance(Key.get(new TypeLiteral<Set<CustomBeanMethodResolver>>() { })));
        var functions = discoverFunctions(injector);
        return new Runtime(injector, workDirectory, processConfiguration, apiClient, sensitiveData, persistenceService,
                taskProviders, taskMethodResolvers, beanMethodResolvers, functions, docker, secrets, locks,
                files, dependencies);
    }

    private static void discoverTasks(Injector injector, TaskHolder<Task> tasks) {
        Iterable<? extends BeanEntry<Named, Task>> entries =
                injector.getInstance(BeanLocator.class).locate(Key.get(Task.class));
        for (var entry : entries) {
            var taskClass = entry.getImplementationClass();
            var name = taskClass.getAnnotation(Named.class);
            if (name == null || name.value().isEmpty()) {
                log.warn("Ignoring task class without a non-empty @Named value: {}", taskClass);
                continue;
            }
            if (ReflectionUtils.findAnnotation(taskClass, Singleton.class) != null) {
                log.warn("Ignoring task class with @Singleton: {}", taskClass);
                continue;
            }
            var previous = tasks.get(name.value());
            if (previous == taskClass) {
                continue;
            }
            tasks.add(name.value(), taskClass);
        }
    }

    private static Map<String, Method> discoverFunctions(Injector injector) {
        var classes = new LinkedHashSet<Class<?>>();
        for (var binding : injector.getAllBindings().values()) {
            classes.add(binding.getKey().getTypeLiteral().getRawType());
        }
        Iterable<? extends BeanEntry<Named, Object>> entries =
                injector.getInstance(BeanLocator.class).locate(Key.get(Object.class));
        for (var entry : entries) {
            classes.add(entry.getImplementationClass());
        }
        var result = new LinkedHashMap<String, Method>();
        for (var clazz : classes) {
            for (var method : clazz.getDeclaredMethods()) {
                var annotation = method.getAnnotation(ELFunction.class);
                if (annotation == null) {
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

    static ObjectMapper objectMapper() {
        var result = new ObjectMapper();
        result.registerModule(new Jdk8Module());
        result.registerModule(new JavaTimeModule());
        return result;
    }

    private static ProcessConfiguration loadProcessConfiguration(Path workDirectory) {
        var instanceIdFile = workDirectory.resolve(Constants.Files.INSTANCE_ID_FILE_NAME);
        UUID instanceId;
        while (true) {
            try {
                if (Files.exists(instanceIdFile)) {
                    var value = Files.readString(instanceIdFile).trim();
                    if (!value.isEmpty()) {
                        instanceId = UUID.fromString(value);
                        break;
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid process instance ID in {}: {}", instanceIdFile, e.getMessage());
            } catch (IOException e) {
                log.warn("Cannot read process instance ID from {}: {}", instanceIdFile, e.getMessage());
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for process instance ID", e);
            }
        }

        var configuration = workDirectory.resolve(Constants.Files.CONFIGURATION_FILE_NAME);
        if (Files.notExists(configuration)) {
            return ProcessConfiguration.builder().instanceId(instanceId).build();
        }
        try (InputStream input = Files.newInputStream(configuration)) {
            return ProcessConfiguration.builder().from(objectMapper().readValue(input, ProcessConfiguration.class))
                    .instanceId(instanceId).build();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load process configuration from " + configuration, e);
        }
    }

    private static ApiClient apiClient(RunnerConfiguration cfg, ProcessConfiguration processCfg) {
        var token = processCfg.processInfo().sessionToken();
        if (token == null) {
            throw new IllegalStateException("Can't initialize the API client: processInfo.sessionToken is not defined");
        }
        var factory = new DefaultApiClientFactory(cfg.api().baseUrl(), Duration.ofMillis(cfg.api().connectTimeout()));
        return factory.create(ApiClientConfiguration.builder().sessionToken(token).build())
                .setReadTimeout(Duration.ofMillis(cfg.api().readTimeout()))
                .setUserAgent("Concord-Runner-v2.5: txId=" + processCfg.instanceId());
    }

    private static ApiClientFactory apiClientFactory(ApiConfiguration cfg, UUID instanceId) {
        var factory = new DefaultApiClientFactory(cfg.baseUrl(), Duration.ofMillis(cfg.connectTimeout()));
        return overrides -> factory.create(overrides)
                .setReadTimeout(Duration.ofMillis(cfg.readTimeout()))
                .setUserAgent("Concord-Runner-v2.5: txId=" + instanceId);
    }

    private static URLClassLoader dependencyClassLoader(Path workDirectory, Collection<String> dependencies) {
        var urls = new LinkedHashSet<URL>();
        addPayloadLibraries(workDirectory, urls);
        if (dependencies != null) {
            for (var dependency : dependencies) {
                var path = Path.of(dependency);
                if (!path.isAbsolute()) {
                    path = workDirectory.resolve(path);
                }
                if (Files.notExists(path)) {
                    throw new IllegalArgumentException("Process dependency does not exist: " + path);
                }
                addDependencyUrl(path, urls);
            }
        }
        return urls.isEmpty() ? null : new URLClassLoader(urls.toArray(URL[]::new), RunnerWiring.class.getClassLoader());
    }

    private static void addPayloadLibraries(Path workDirectory, Set<URL> urls) {
        var libDirectory = workDirectory.resolve("lib");
        if (Files.notExists(libDirectory)) {
            return;
        }
        try (var files = Files.list(libDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .forEach(path -> addDependencyUrl(path, urls));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read process libraries from " + libDirectory, e);
        }
    }

    private static void addDependencyUrl(Path path, Set<URL> urls) {
        try {
            urls.add(path.toUri().toURL());
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid process dependency: " + path, e);
        }
    }

    record Runtime(Injector injector, WorkingDirectory workingDirectory, ProcessConfiguration processConfiguration,
                   ApiClient apiClient, SensitiveDataHolder sensitiveData, PersistenceService persistenceService,
                   List<TaskProvider> taskProviders,
                   List<CustomTaskMethodResolver> taskMethodResolvers,
                   List<CustomBeanMethodResolver> beanMethodResolvers,
                   Map<String, Method> functions,
                   com.walmartlabs.concord.runtime.v2.sdk.DockerService dockerService,
                   com.walmartlabs.concord.runtime.v2.sdk.SecretService secretService,
                   com.walmartlabs.concord.runtime.v2.sdk.LockService lockService,
                   com.walmartlabs.concord.runtime.v2.sdk.FileService fileService,
                   ClassLoader dependencyClassLoader) {
    }

    private record SdkApiConfiguration(RunnerConfiguration cfg) implements ApiConfiguration {
        @Override
        public String baseUrl() {
            return cfg.api().baseUrl();
        }

        @Override
        public int connectTimeout() {
            return cfg.api().connectTimeout();
        }

        @Override
        public int readTimeout() {
            return cfg.api().readTimeout();
        }
    }

    private static final class ContextScope implements Provider<Context> {
        private final ThreadLocal<Context> current = new ThreadLocal<>();

        @Override
        public Context get() {
            var value = current.get();
            if (value == null) {
                throw new IllegalStateException("A task requested Context outside its invocation");
            }
            return value;
        }

        <T> T with(Context context, java.util.concurrent.Callable<T> action) {
            var previous = current.get();
            current.set(context);
            try {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Cannot create task", e);
            } finally {
                if (previous == null) {
                    current.remove();
                } else {
                    current.set(previous);
                }
            }
        }
    }

    private static final class DiscoveredTasks implements TaskProvider {
        private final Injector injector;
        private final TaskHolder<Task> tasks;
        private final ContextScope contexts;

        @Inject
        private DiscoveredTasks(Injector injector, TaskHolder<Task> tasks, ContextScope contexts) {
            this.injector = injector;
            this.tasks = tasks;
            this.contexts = contexts;
        }

        @Override
        public Task createTask(Context context, String key) {
            var taskClass = tasks.get(key);
            return taskClass == null ? null : contexts.with(context, () -> injector.getInstance(taskClass));
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {

            return tasks.get(key);
        }

        @Override
        public boolean hasTask(String key) {
            return tasks.get(key) != null;
        }

        @Override
        public Set<String> names() {
            return tasks.keys();
        }
    }

    private static final class TaskProvidersProvider implements Provider<List<TaskProvider>> {
        private final Provider<Set<TaskProvider>> providers;

        @Inject
        private TaskProvidersProvider(Provider<Set<TaskProvider>> providers) {
            this.providers = providers;
        }

        @Override
        public List<TaskProvider> get() {
            return List.copyOf(providers.get());
        }
    }

    static final class PersistenceService {
        private final Path workDirectory;
        private final ObjectMapper mapper;

        PersistenceService(Path workDirectory, ObjectMapper mapper) {
            this.workDirectory = workDirectory;
            this.mapper = mapper;
        }

        void persistSessionFile(String name, Set<String> values) throws IOException {
            persist(sessionFile(name), values);
        }

        void mergeSessionFile(String name, SensitiveDataHolder holder) throws IOException {
            merge(sessionFile(name), holder);
        }

        void persist(Path file, Set<String> values) throws IOException {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), values);
        }

        void merge(Path file, SensitiveDataHolder holder) throws IOException {
            if (Files.notExists(file)) {
                return;
            }
            var values = mapper.readValue(file.toFile(), new TypeReference<Set<String>>() { });
            if (values != null) {
                holder.addAll(values);
            }
        }

        private Path sessionFile(String name) {
            return workDirectory.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                    .resolve(Constants.Files.JOB_SESSION_FILES_DIR_NAME).resolve(name);
        }
    }

    static final class SensitiveDataRegistry implements SensitiveDataHolder {
        private final Set<String> values = ConcurrentHashMap.newKeySet();

        @Override
        public Set<String> get() {
            return Set.copyOf(values);
        }

        @Override
        public void add(String value) {
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }

        @Override
        public void addAll(Collection<String> values) {
            values.forEach(this::add);
        }
    }

    private RunnerWiring() {
    }
}
