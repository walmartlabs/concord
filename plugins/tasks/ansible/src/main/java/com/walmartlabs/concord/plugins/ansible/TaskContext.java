package com.walmartlabs.concord.plugins.ansible;

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

import com.walmartlabs.concord.sdk.Context;

import java.nio.file.Path;
import java.util.Map;

public class TaskContext {

    private final Context context;
    private final Map<String, Object> defaults;
    private final Path workDir;
    private final Path tmpDir;
    private final boolean debug;
    private final Map<String, Object> args;

    public TaskContext(Context context, Map<String, Object> defaults, Path workDir, Path tmpDir, boolean debug, Map<String, Object> args) {
        this.context = context;
        this.defaults = defaults;
        this.workDir = workDir;
        this.tmpDir = tmpDir;
        this.debug = debug;
        this.args = args;
    }

    public Context getContext() {
        return context;
    }

    public Map<String, Object> getDefaults() {
        return defaults;
    }

    public Path getWorkDir() {
        return workDir;
    }

    public Path getTmpDir() {
        return tmpDir;
    }

    public boolean isDebug() {
        return debug;
    }

    public Map<String, Object> getArgs() {
        return args;
    }
}
