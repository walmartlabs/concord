package com.walmartlabs.concord.runtime.v2.runner.context;

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

import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class TaskContextImpl implements TaskContext {

    private final Context delegate;
    private final String taskName;
    private final Map<String, Object> input;

    public TaskContextImpl(Context delegate, String taskName, Map<String, Object> input) {
        this.delegate = delegate;
        this.taskName = taskName;
        this.input = input;
    }

    @Override
    public GlobalVariables globalVariables() {
        return delegate.globalVariables();
    }

    @Override
    public Path workingDirectory() {
        return delegate.workingDirectory();
    }

    @Override
    public UUID processInstanceId() {
        return delegate.processInstanceId();
    }

    @Override
    public Execution execution() {
        return delegate.execution();
    }

    @Override
    public Compiler compiler() {
        return delegate.compiler();
    }

    @Override
    public <T> T interpolate(Object v, Class<T> type) {
        return delegate.interpolate(v, type);
    }

    @Override
    public String taskName() {
        return taskName;
    }

    @Override
    public Map<String, Object> input() {
        return input;
    }
}
