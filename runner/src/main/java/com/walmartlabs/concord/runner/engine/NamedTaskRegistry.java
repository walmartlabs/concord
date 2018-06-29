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

import com.google.inject.Injector;
import com.walmartlabs.concord.common.DynamicTaskRegistry;
import io.takari.bpm.task.ServiceTaskRegistry;
import org.eclipse.sisu.EagerSingleton;

import javax.inject.Inject;
import javax.inject.Named;

@Named
@EagerSingleton
public class NamedTaskRegistry extends AbstractTaskRegistry implements ServiceTaskRegistry {

    private final DynamicTaskRegistry dynamicTasks;
    private final Injector injector;

    @Inject
    public NamedTaskRegistry(Injector injector,
                             DynamicTaskRegistry dynamicTasks) {

        this.injector = injector;
        this.dynamicTasks = dynamicTasks;

        // TODO: find a way to inject task classes directly
        TaskClassHolder h = TaskClassHolder.getInstance();
        h.getTasks().forEach(this::register);
    }

    @Override
    public Object getByKey(String key) {
        Object o = null;
        if (dynamicTasks != null) {
            o = dynamicTasks.getByKey(key);
        }

        return o != null ? o : get(key, injector);
    }
}
