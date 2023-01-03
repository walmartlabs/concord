package com.walmartlabs.concord.plugins.dynamic;

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

import groovy.lang.GroovyClassLoader;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class TaskLoader {

    private final TaskRegistry taskRegistry;
    private final GroovyClassLoader classLoader;

    public TaskLoader(TaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
        this.classLoader = new GroovyClassLoader();
    }

    @SuppressWarnings("rawtypes")
    public void load(Path path) throws Exception {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Class clazz = classLoader.parseClass(file.toFile());
                    taskRegistry.register(clazz);
                } catch (Exception e) {
                    throw new RuntimeException("Error while loading a task: " + file, e);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }
}
