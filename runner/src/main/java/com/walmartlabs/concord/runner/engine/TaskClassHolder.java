package com.walmartlabs.concord.runner.engine;

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


import com.walmartlabs.concord.sdk.Task;

import java.util.HashMap;
import java.util.Map;

public class TaskClassHolder {

    private static final TaskClassHolder INSTANCE = new TaskClassHolder();

    public static TaskClassHolder getInstance() {
        return INSTANCE;
    }

    private final Map<String, Class<? extends Task>> tasks = new HashMap<>();

    public void register(String name, Class<? extends Task> task) {
        tasks.putIfAbsent(name, task);
    }

    public Map<String, Class<? extends Task>> getTasks() {
        return tasks;
    }

    private TaskClassHolder() {
    }
}
