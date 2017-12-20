package com.walmartlabs.concord.runner.engine.el;

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

import com.walmartlabs.concord.sdk.InjectVariable;
import io.takari.bpm.task.ServiceTaskRegistry;
import io.takari.bpm.task.ServiceTaskResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.el.ELContext;
import javax.inject.Singleton;
import java.lang.reflect.Field;

public class TaskResolver extends ServiceTaskResolver {

    private static final Logger log = LoggerFactory.getLogger(TaskResolver.class);

    public TaskResolver(ServiceTaskRegistry registry) {
        super(registry);
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Object o = super.getValue(context, base, property);
        if (o != null && context.isPropertyResolved()) {
            injectVariables(context, o, property);
        }
        return o;
    }

    private static void injectVariables(ELContext context, Object o, Object property) {
        Class clazz = o.getClass();
        boolean isSingleton = clazz.isAnnotationPresent(Singleton.class);
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                String v = getAnnotationValue(f);
                if (v == null) {
                    continue;
                }

                inject(f, v, context, o, property, isSingleton);
            }

            clazz = clazz.getSuperclass();
        }
    }

    private static String getAnnotationValue(Field f) {
        InjectVariable iv = f.getAnnotation(InjectVariable.class);
        if (iv != null) {
            return iv.value();
        } else {
            com.walmartlabs.concord.common.InjectVariable iv2 = f.getAnnotation(com.walmartlabs.concord.common.InjectVariable.class);
            if (iv2 != null) {
                return iv2.value();
            }
        }
        return null;
    }

    private static void inject(Field f, String value, ELContext context, Object base, Object property, boolean isSingleton) {
        if (isSingleton) {
            log.warn("invoke ['{}', '{}'] -> @InjectVariable cannot be used in @Singleton tasks: '{}'", base, property, f.getName());
            return;
        }

        try {
            Object variableValue = ResolverUtils.getVariable(context, value);

            if (!f.isAccessible()) {
                f.setAccessible(true);
            }
            f.set(base, variableValue);

            log.debug("invoke ['{}', '{}'] -> set value '{}' for '{}'", base, property, f.getName(), variableValue);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error while setting property '" + f.getName() + "': " + e.getMessage(), e);
        }
    }
}
