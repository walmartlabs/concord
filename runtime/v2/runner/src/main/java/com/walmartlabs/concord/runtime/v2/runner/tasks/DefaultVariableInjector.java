package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.runner.DefaultTaskVariables;
import com.walmartlabs.concord.runtime.v2.sdk.DefaultVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.util.Map;

@Named
@Singleton
public class DefaultVariableInjector {

    private final DefaultTaskVariables defaultVariables;
    private final ObjectMapper objectMapper;

    @Inject
    public DefaultVariableInjector(DefaultTaskVariables defaultVariables) {
        this.defaultVariables = defaultVariables;
        this.objectMapper = new ObjectMapper();
    }

    public Task inject(Task task) {
        Class<?> clazz = task.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                String v = getAnnotationValue(f, getTaskName(task));
                if (v == null) {
                    continue;
                }

                Map<String, Object> variables = defaultVariables.get(v);
                if (variables != null) {
                    inject(task, f, coerce(variables, f.getType()));
                }
            }

            clazz = clazz.getSuperclass();
        }
        return task;
    }

    private Object coerce(Map<String, Object> variables, Class<?> type) {
        if (Map.class.isAssignableFrom(type)) {
            return variables;
        }
        return objectMapper.convertValue(variables, type);
    }

    private static String getTaskName(Task task) {
        Named n = task.getClass().getAnnotation(Named.class);
        if (n != null) {
            return n.value();
        }
        return null;
    }

    private static String getAnnotationValue(Field f, String defaultValue) {
        DefaultVariables dv = f.getAnnotation(DefaultVariables.class);
        if (dv != null) {
            return "".equals(dv.value()) ? defaultValue : dv.value();
        }
        return null;
    }

    private static void inject(Task task, Field f, Object variables) {
        try {
            if (!f.isAccessible()) {
                f.setAccessible(true);
            }
            f.set(task, variables);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error while setting property '" + f.getName() + "': " + e.getMessage(), e);
        }
    }
}
