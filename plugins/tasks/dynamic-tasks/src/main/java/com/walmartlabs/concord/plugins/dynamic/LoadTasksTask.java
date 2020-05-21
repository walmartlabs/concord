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

import com.walmartlabs.concord.common.DynamicTaskRegistry;
import com.walmartlabs.concord.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named("loadTasks")
@SuppressWarnings("unused")
public class LoadTasksTask implements Task {

    private final TaskRegistry taskRegistry;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    private Context ctx;

    @Inject
    public LoadTasksTask(DynamicTaskRegistry registry) {
        this.taskRegistry = registry::register;
    }

    public void call(String path) throws Exception {
        Path workDir = Paths.get(ContextUtils.getString(ctx, Constants.Context.WORK_DIR_KEY));

        Path src = workDir.resolve(path);
        if (!Files.exists(src) || !Files.isDirectory(src)) {
            throw new RuntimeException("Path not found or not a directory: " + workDir.relativize(src));
        }

        new TaskLoader(taskRegistry).load(src);
    }
}
