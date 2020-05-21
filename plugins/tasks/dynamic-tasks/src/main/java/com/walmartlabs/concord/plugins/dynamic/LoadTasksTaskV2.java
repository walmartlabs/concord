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
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

@Named("loadTasks")
@SuppressWarnings("unused")
public class LoadTasksTaskV2 implements Task {

    private final TaskRegistry taskRegistry;
    private final WorkingDirectory workDir;

    @Inject
    @SuppressWarnings("unchecked")
    public LoadTasksTaskV2(Injector injector, WorkingDirectory workDir) {
        this.taskRegistry = clazz -> {
            if (Task.class.isAssignableFrom(clazz)) {
                injector.getBinding(clazz);
            } else {
                throw new RuntimeException("Unknown task type: " + clazz);
            }
        };
        this.workDir = workDir;
    }

    @Override
    public Serializable execute(TaskContext ctx) throws Exception {
        String path = MapUtils.assertString(ctx.input(), "0");

        Path src = workDir.getValue().resolve(path);
        if (!Files.exists(src) || !Files.isDirectory(src)) {
            throw new RuntimeException("Path not found or not a directory: " + workDir.getValue().relativize(src));
        }

        new TaskLoader(taskRegistry).load(src);
        return null;
    }
}
