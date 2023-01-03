package com.walmartlabs.concord.runner.engine;

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

import com.google.inject.Injector;
import com.walmartlabs.concord.common.DynamicTaskRegistry;
import com.walmartlabs.concord.runner.TaskClasses;
import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.task.ServiceTaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Named
@Singleton
public class TaskRegistry implements ServiceTaskRegistry, DynamicTaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(TaskRegistry.class);

    private final Injector injector;
    private final TaskClasses taskClasses;
    private final Map<String, Class<? extends Task>> dynamicTasks = new ConcurrentHashMap<>();

    @Inject
    public TaskRegistry(Injector injector, TaskClasses taskClasses) {
        this.injector = injector;
        this.taskClasses = taskClasses;
    }

    @Override
    public Task getByKey(String key) {
        Class<? extends Task> taskClass = taskClasses.get(key);
        if(taskClass == null) {
            taskClass = dynamicTasks.get(key);
            if (taskClass == null) {
                return null;
            }
        }

        return injector.getInstance(taskClass);
    }

    @Override
    public void register(Class<? extends Task> taskClass) {
        Named n = taskClass.getAnnotation(Named.class);
        if (n == null) {
            throw new IllegalArgumentException("Tasks must be annotated with @Named");
        }

        dynamicTasks.put(n.value(), taskClass);
    }
}
