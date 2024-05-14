package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.runtime.v2.ProjectLoadListener;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

public class ProjectLoadListeners implements ProjectLoadListener {

    private final Collection<ProjectLoadListener> listeners;

    @Inject
    public ProjectLoadListeners(Set<ProjectLoadListener> listeners) {
        this.listeners = listeners;
    }

    @Override
    public void afterFlowDefinitionLoaded(Path filename) {
        listeners.forEach(l -> l.afterFlowDefinitionLoaded(filename));
    }

    @Override
    public void afterProjectLoaded() {
        listeners.forEach(ProjectLoadListener::afterProjectLoaded);
    }
}
