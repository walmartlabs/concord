package com.walmartlabs.concord.plugins.input;

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

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.runtime.v2.ProjectLoadListener;
import com.walmartlabs.concord.svm.ExecutionListener;

import javax.inject.Named;

@Named
public class InputParamsAssertModule implements com.google.inject.Module {

    @Override
    public void configure(Binder binder) {
        Multibinder<ExecutionListener> taskProviders = Multibinder.newSetBinder(binder, ExecutionListener.class);
        taskProviders.addBinding().to(FlowCallListener.class);

        Multibinder<ProjectLoadListener> projectLoadListeners = Multibinder.newSetBinder(binder, ProjectLoadListener.class);
        projectLoadListeners.addBinding().to(ConcordYamlProcessor.class);
    }
}
