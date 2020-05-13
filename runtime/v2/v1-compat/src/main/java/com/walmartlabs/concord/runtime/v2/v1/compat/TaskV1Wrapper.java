package com.walmartlabs.concord.runtime.v2.v1.compat;

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

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.InjectVariable;

import javax.inject.Singleton;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TaskV1Wrapper implements Task {

    private final com.walmartlabs.concord.sdk.Task v1Task;
    private final Path workDir;

    public TaskV1Wrapper(com.walmartlabs.concord.sdk.Task v1Task, Path workDir) {
        this.v1Task = v1Task;
        this.workDir = workDir;
    }

    @Override
    public Serializable execute(TaskContext ctx) throws Exception {
        Map<String, Serializable> result = new HashMap<>();
        ContextV1Wrapper v1Context = new ContextV1Wrapper(ctx, result);
        v1Context.setVariable(Constants.Context.WORK_DIR_KEY, workDir.toString());

        Map<String, Object> allVars = v1Context.getAllVariables();
        allVars.put(Constants.Context.CONTEXT_KEY, v1Context);

        injectVariables(v1Task, allVars);

        v1Task.execute(v1Context);

        v1Context.removeVariable(Constants.Context.WORK_DIR_KEY);

        return (Serializable) result;
    }

    private static void injectVariables(Object o, Map<String, Object> variables) {
        Class<?> clazz = o.getClass();
        boolean isSingleton = clazz.isAnnotationPresent(Singleton.class);
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                String v = getAnnotationValue(f);
                if (v == null) {
                    continue;
                }

                inject(f, v, o, variables, isSingleton);
            }

            clazz = clazz.getSuperclass();
        }
    }

    private static String getAnnotationValue(Field f) {
        InjectVariable iv = f.getAnnotation(InjectVariable.class);
        if (iv != null) {
            return iv.value();
        }
        return null;
    }

    private static void inject(Field f, String value, Object base, Map<String, Object> variables, boolean isSingleton) {
        if (isSingleton) {
            return;
        }

        try {
            Object variableValue = variables.get(value);
            if (!f.isAccessible()) {
                f.setAccessible(true);
            }
            f.set(base, variableValue);

        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error while setting property '" + f.getName() + "': " + e.getMessage(), e);
        }
    }
}
