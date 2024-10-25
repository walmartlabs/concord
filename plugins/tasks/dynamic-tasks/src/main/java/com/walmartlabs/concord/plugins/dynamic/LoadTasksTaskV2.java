package com.walmartlabs.concord.plugins.dynamic;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Files;
import java.nio.file.Path;

@Named("loadTasks")
@DryRunReady
@SuppressWarnings("unused")
public class LoadTasksTaskV2 implements Task {

    private final Context context;
    private final TaskRegistry taskRegistry;

    @Inject
    @SuppressWarnings("unchecked")
    public LoadTasksTaskV2(Injector injector, Context context) {
        this.taskRegistry = clazz -> {
            if (Task.class.isAssignableFrom(clazz)) {
                injector.getBinding(clazz);
            } else {
                throw new RuntimeException("Unknown task type: " + clazz);
            }
        };
        this.context = context;
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        String path = input.assertString("path");

        Path src = context.workingDirectory().resolve(path);
        if (!Files.exists(src) || !Files.isDirectory(src)) {
            throw new RuntimeException("Path not found or not a directory: " + context.workingDirectory().relativize(src));
        }

        new TaskLoader(taskRegistry).load(src);
        return TaskResult.success();
    }
}
