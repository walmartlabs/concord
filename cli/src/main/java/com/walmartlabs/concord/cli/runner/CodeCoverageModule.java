package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.cli.runner.codecoverage.CodeCoverage;
import com.walmartlabs.concord.runtime.v2.runner.DefaultEventReportingService;
import com.walmartlabs.concord.runtime.v2.runner.EventReportingService;
import com.walmartlabs.concord.runtime.v2.runner.ProcessEventWriter;
import com.walmartlabs.concord.runtime.v2.runner.remote.EventRecordingExecutionListener;
import com.walmartlabs.concord.runtime.v2.runner.remote.TaskCallEventRecordingListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.svm.ExecutionListener;

import javax.inject.Singleton;

public class CodeCoverageModule extends AbstractModule {

    private final boolean enabled;

    public CodeCoverageModule(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void configure() {
        if (!enabled) {
            return;
        }

        bind(ProcessEventWriter.class).to(CliProcessEventsWriter.class).in(Singleton.class);
        bind(EventReportingService.class).to(DefaultEventReportingService.class).in(Singleton.class);

        Multibinder<ExecutionListener> executionListeners = Multibinder.newSetBinder(binder(), ExecutionListener.class);
        executionListeners.addBinding().to(EventRecordingExecutionListener.class);
        executionListeners.addBinding().to(CliProcessEventsWriter.class);
        executionListeners.addBinding().to(CodeCoverage.class);

        Multibinder<TaskCallListener> taskCallListeners = Multibinder.newSetBinder(binder(), TaskCallListener.class);
        taskCallListeners.addBinding().to(TaskCallEventRecordingListener.class);
    }
}
