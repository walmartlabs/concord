
package com.walmartlabs.concord.runtime.v2.runner.script;

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

import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class ScriptContext implements Context {

    private final Context delegate;
    private final ScriptVariables variables;

    public ScriptContext(Context delegate) {
        this.delegate = delegate;
        this.variables = new ScriptVariables(delegate.variables());
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
    public Variables variables() {
        return variables;
    }

    @Override
    public Variables defaultVariables() {
        return delegate.defaultVariables();
    }

    @Override
    public FileService fileService() {
        return delegate.fileService();
    }

    @Override
    public DockerService dockerService() {
        return delegate.dockerService();
    }

    @Override
    public SecretService secretService() {
        return delegate.secretService();
    }

    @Override
    public LockService lockService() {
        return delegate.lockService();
    }

    @Override
    public ApiConfiguration apiConfiguration() {
        return delegate.apiConfiguration();
    }

    @Override
    public ProcessConfiguration processConfiguration() {
        return delegate.processConfiguration();
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
    public <T> T eval(Object v, Class<T> type) {
        return delegate.eval(v, type);
    }

    @Override
    public void suspend(String eventName) {
        delegate.suspend(eventName);
    }

    @Override
    public String suspendResume(Map<String, Serializable> state) {
        return delegate.suspendResume(state);
    }
}