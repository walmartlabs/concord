package com.walmartlabs.concord.runtime.v25.runner.task;

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

import com.walmartlabs.concord.runtime.v2.sdk.ApiConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.CustomBeanMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.runtime.v2.sdk.FileService;
import com.walmartlabs.concord.runtime.v2.sdk.LockService;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.runtime.v25.model.Values;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TaskEnvironment(UUID processInstanceId, Path workingDirectory, boolean debug, boolean dryRun,
                              Map<String, Map<String, Object>> defaultTaskVariables,
                              DockerService dockerService, SecretService secretService,
                              LockService lockService, ApiConfiguration apiConfiguration,
                              ProcessConfiguration processConfiguration, TaskRuntime.TaskInterceptor taskInterceptor,
                              List<CustomTaskMethodResolver> taskMethodResolvers,
                              List<CustomBeanMethodResolver> beanMethodResolvers,
                              Map<Class<?>, Object> services, FileService fileService) {

    public TaskEnvironment {
        processInstanceId = processInstanceId != null ? processInstanceId : UUID.randomUUID();
        workingDirectory = (workingDirectory != null ? workingDirectory : Path.of(".")).toAbsolutePath().normalize();
        var defaults = new LinkedHashMap<String, Map<String, Object>>();
        (defaultTaskVariables != null ? defaultTaskVariables : Map.<String, Map<String, Object>>of())
                .forEach((name, values) -> defaults.put(name, Values.map(values)));
        defaultTaskVariables = Collections.unmodifiableMap(defaults);
        taskMethodResolvers = List.copyOf(taskMethodResolvers != null ? taskMethodResolvers : List.of());
        beanMethodResolvers = List.copyOf(beanMethodResolvers != null ? beanMethodResolvers : List.of());
        services = services != null ? Map.copyOf(services) : Map.of();
        fileService = fileService != null ? fileService : files(workingDirectory);
    }

    public TaskEnvironment(UUID processInstanceId, Path workingDirectory, boolean debug, boolean dryRun,
                           Map<String, Map<String, Object>> defaultTaskVariables, DockerService dockerService,
                           SecretService secretService, LockService lockService, ApiConfiguration apiConfiguration,
                           ProcessConfiguration processConfiguration, TaskRuntime.TaskInterceptor taskInterceptor,
                           List<CustomTaskMethodResolver> taskMethodResolvers,
                           List<CustomBeanMethodResolver> beanMethodResolvers, Map<Class<?>, Object> services) {
        this(processInstanceId, workingDirectory, debug, dryRun, defaultTaskVariables, dockerService, secretService,
                lockService, apiConfiguration, processConfiguration, taskInterceptor, taskMethodResolvers,
                beanMethodResolvers, services, null);
    }

    private static FileService files(Path workingDirectory) {
        var temporaryDirectory = workingDirectory.resolve(".concord").resolve("tmp");
        return new FileService() {
            @Override
            public Path createTempFile(String prefix, String suffix) throws IOException {
                Files.createDirectories(temporaryDirectory);
                return Files.createTempFile(temporaryDirectory, prefix, suffix);
            }

            @Override
            public Path createTempDirectory(String prefix) throws IOException {
                Files.createDirectories(temporaryDirectory);
                return Files.createTempDirectory(temporaryDirectory, prefix);
            }
        };
    }

    public static TaskEnvironment local(Path workingDirectory) {
        return new TaskEnvironment(UUID.randomUUID(), workingDirectory, false, false, Map.of(),
                null, null, null, null, null, null, List.of(), List.of(), Map.of());
    }
}
