package com.walmartlabs.concord.plugins.dynamic;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.common.DynamicTaskRegistry;
import com.walmartlabs.concord.sdk.Task;
import groovy.lang.GroovyClassLoader;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Named("loadTasks")
public class LoadTasksTask implements Task {

    private final DynamicTaskRegistry taskRegistry;
    private final GroovyClassLoader classLoader;

    @Inject
    public LoadTasksTask(DynamicTaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
        this.classLoader = new GroovyClassLoader();
    }

    public void call(String path) throws Exception {
        Path p = Paths.get(path);
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    register(classLoader.parseClass(file.toFile()));
                } catch (Exception e) {
                    throw new RuntimeException("Error while loading a task: " + file, e);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void register(Class c) throws Exception {
        taskRegistry.register(c);
    }
}
