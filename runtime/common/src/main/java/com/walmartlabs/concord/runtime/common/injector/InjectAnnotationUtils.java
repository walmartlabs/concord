package com.walmartlabs.concord.runtime.common.injector;

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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.Set;

public final class InjectAnnotationUtils {

    public static final String JAVAX_INJECT = "javax.inject.Inject";
    public static final String JAVAX_NAMED = "javax.inject.Named";
    public static final String JAVAX_SINGLETON = "javax.inject.Singleton";
    public static final String JAKARTA_INJECT = "jakarta.inject.Inject";
    public static final String JAKARTA_NAMED = "jakarta.inject.Named";
    public static final String JAKARTA_SINGLETON = "jakarta.inject.Singleton";

    private static final Set<String> NAMED_ANNOTATIONS = Set.of(JAVAX_NAMED, JAKARTA_NAMED);
    private static final Set<String> SINGLETON_ANNOTATIONS = Set.of(JAVAX_SINGLETON, JAKARTA_SINGLETON);

    public static Optional<String> findNamedValue(Class<?> klass) {
        return findAnnotation(klass, NAMED_ANNOTATIONS)
                .map(InjectAnnotationUtils::annotationValue);
    }

    public static Optional<String> findNamedValue(Annotation[] annotations) {
        return findAnnotation(annotations, NAMED_ANNOTATIONS)
                .map(InjectAnnotationUtils::annotationValue);
    }

    public static boolean hasSingleton(Class<?> klass) {
        return findAnnotation(klass, SINGLETON_ANNOTATIONS).isPresent();
    }

    public static boolean hasJakartaInject(AnnotatedElement element) {
        return hasAnnotation(element, JAKARTA_INJECT);
    }

    private static boolean hasAnnotation(AnnotatedElement element, String annotationName) {
        return findAnnotation(element.getAnnotations(), Set.of(annotationName)).isPresent();
    }

    private static Optional<Annotation> findAnnotation(Class<?> klass, Set<String> annotationNames) {
        var annotation = findAnnotation(klass.getAnnotations(), annotationNames);
        if (annotation.isPresent()) {
            return annotation;
        }

        for (var ifc : klass.getInterfaces()) {
            annotation = findAnnotation(ifc, annotationNames);
            if (annotation.isPresent()) {
                return annotation;
            }
        }

        var superClass = klass.getSuperclass();
        if (superClass == null || superClass == Object.class) {
            return Optional.empty();
        }

        return findAnnotation(superClass, annotationNames);
    }

    private static Optional<Annotation> findAnnotation(Annotation[] annotations, Set<String> annotationNames) {
        for (var annotation : annotations) {
            if (annotationNames.contains(annotation.annotationType().getName())) {
                return Optional.of(annotation);
            }
        }
        return Optional.empty();
    }

    private static String annotationValue(Annotation annotation) {
        try {
            return (String) annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Invalid @Named annotation: " + annotation.annotationType().getName(), e);
        }
    }

    private InjectAnnotationUtils() {
    }
}
