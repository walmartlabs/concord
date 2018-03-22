package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class DynamicTaskRegistryImpl extends AbstractTaskRegistry implements DynamicTaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(DynamicTaskRegistryImpl.class);

    private final Injector injector;

    @Inject
    public DynamicTaskRegistryImpl(Injector injector) {
        this.injector = injector;
    }

    @Override
    public Task getByKey(String key) {
        return get(key, injector);
    }

    @SuppressWarnings("deprecation")
    public void register(Class<? extends Task> taskClass) {
        Named n = taskClass.getAnnotation(Named.class);
        if (n == null) {
            throw new IllegalArgumentException("Tasks must be annotated with @Named");
        }

        if (com.walmartlabs.concord.common.Task.class.isAssignableFrom(taskClass)) {
            log.warn("{}: '{}' is deprecated, please use '{}'", n,
                    com.walmartlabs.concord.common.Task.class.getName(),
                    Task.class.getName());
        }

        register(n.value(), taskClass);
    }
}
