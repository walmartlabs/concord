package com.walmartlabs.concord.runtime.v2.runner.tasks;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.walmartlabs.concord.runtime.common.injector.InjectAnnotationUtils;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;

public class TaskInstanceFactory {

    private final Injector injector;

    @Inject
    public TaskInstanceFactory(Injector injector) {
        this.injector = injector;
    }

    public <T> T create(Class<T> klass) {
        var constructor = findJakartaInjectConstructor(klass);
        if (constructor == null && !hasJakartaMemberInjection(klass)) {
            return injector.getInstance(klass);
        }

        T instance;
        if (constructor != null) {
            instance = newInstance(constructor);
            injector.injectMembers(instance);
        } else {
            instance = injector.getInstance(klass);
        }

        injectJakartaMembers(instance);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private static <T> Constructor<T> findJakartaInjectConstructor(Class<T> klass) {
        Constructor<?> result = null;
        for (var constructor : klass.getDeclaredConstructors()) {
            if (!InjectAnnotationUtils.hasJakartaInject(constructor)) {
                continue;
            }

            if (result != null) {
                throw new ProvisionException("Multiple Jakarta @Inject constructors in " + klass.getName());
            }
            result = constructor;
        }
        return (Constructor<T>) result;
    }

    private static boolean hasJakartaMemberInjection(Class<?> klass) {
        for (var c : hierarchy(klass)) {
            for (var field : c.getDeclaredFields()) {
                if (InjectAnnotationUtils.hasJakartaInject(field)) {
                    return true;
                }
            }

            for (var method : c.getDeclaredMethods()) {
                if (InjectAnnotationUtils.hasJakartaInject(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    private <T> T newInstance(Constructor<T> constructor) {
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args(constructor.getGenericParameterTypes(), constructor.getParameterAnnotations()));
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ProvisionException("Error while creating task " + constructor.getDeclaringClass().getName(), e);
        } catch (InvocationTargetException e) {
            throw new ProvisionException("Error while creating task " + constructor.getDeclaringClass().getName(), e.getCause());
        }
    }

    private void injectJakartaMembers(Object instance) {
        for (var c : hierarchy(instance.getClass())) {
            for (var field : c.getDeclaredFields()) {
                if (InjectAnnotationUtils.hasJakartaInject(field)) {
                    injectField(instance, field);
                }
            }

            for (var method : c.getDeclaredMethods()) {
                if (InjectAnnotationUtils.hasJakartaInject(method)) {
                    injectMethod(instance, method);
                }
            }
        }
    }

    private void injectField(Object instance, Field field) {
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
            throw new ProvisionException("Unsupported Jakarta @Inject field: " + field);
        }

        try {
            field.setAccessible(true);
            field.set(instance, getInstance(field.getGenericType(), field.getAnnotations()));
        } catch (IllegalAccessException e) {
            throw new ProvisionException("Error while injecting field " + field, e);
        }
    }

    private void injectMethod(Object instance, Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new ProvisionException("Unsupported Jakarta @Inject method: " + method);
        }

        try {
            method.setAccessible(true);
            method.invoke(instance, args(method.getGenericParameterTypes(), method.getParameterAnnotations()));
        } catch (IllegalAccessException e) {
            throw new ProvisionException("Error while injecting method " + method, e);
        } catch (InvocationTargetException e) {
            throw new ProvisionException("Error while injecting method " + method, e.getCause());
        }
    }

    private Object[] args(Type[] parameterTypes, Annotation[][] parameterAnnotations) {
        var result = new Object[parameterTypes.length];
        for (var i = 0; i < parameterTypes.length; i++) {
            result[i] = getInstance(parameterTypes[i], parameterAnnotations[i]);
        }
        return result;
    }

    private Object getInstance(Type type, Annotation[] annotations) {
        var named = InjectAnnotationUtils.findNamedValue(annotations);
        if (named.isPresent()) {
            return injector.getInstance(Key.get(TypeLiteral.get(type), Names.named(named.get())));
        }

        return injector.getInstance(Key.get(TypeLiteral.get(type)));
    }

    private static Iterable<Class<?>> hierarchy(Class<?> klass) {
        var result = new ArrayList<Class<?>>();
        for (var c = klass; c != null && c != Object.class; c = c.getSuperclass()) {
            result.add(c);
        }
        Collections.reverse(result);
        return result;
    }
}
