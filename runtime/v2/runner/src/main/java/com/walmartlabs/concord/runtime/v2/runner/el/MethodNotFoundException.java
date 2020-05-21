package com.walmartlabs.concord.runtime.v2.runner.el;

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

import org.apache.commons.text.similarity.LevenshteinDistance;

import javax.el.ELException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MethodNotFoundException extends ELException {

    private static final int MAX_HINTS = 3;

    public MethodNotFoundException(Object base, Object method) {
        super(formatMessage(base, method));
    }

    private static String formatMessage(Object base, Object method) {
        Class<?> baseClass = getBaseClass(base);

        String baseName = baseClass != null ? baseClass.getName() : "n/a";
        String methodName = method != null ? method.toString() : "n/a";

        StringBuilder b = new StringBuilder();
        b.append("Can't find '").append(methodName).append("' method in ").append(baseName).append(".\n")
                .append("Check the task's or type's available methods and their signatures.");

        String hint = getMethodHint(baseClass, methodName);
        if (hint != null) {
            b.append("\n").append(hint);
        }

        return b.toString();
    }

    /**
     * Returns the base's class.
     * Tries to unwrap Guice proxy classes to get the original class.
     */
    private static Class<?> getBaseClass(Object base) {
        if (base == null) {
            return null;
        }

        Class<?> klass = base.getClass();

        String name = klass.getName();
        if (name.contains("EnhancerByGuice")) {
            return klass.getSuperclass();
        }

        return klass;
    }

    /**
     * Creates a type hint, showing methods of the specified base class
     * in order of their Levenshtein distance from the method name speficied by the user.
     */
    private static String getMethodHint(Class<?> baseClass, Object method) {
        if (baseClass == null || method == null) {
            return null;
        }

        String methodName = method.toString();

        LevenshteinDistance distance = LevenshteinDistance.getDefaultInstance();
        List<String> candidates = Arrays.stream(baseClass.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .map(s -> "'" + s + "'")
                .sorted(Comparator.comparingInt(m -> distance.apply(methodName, m)))
                .limit(MAX_HINTS)
                .distinct()
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }

        return "Did you mean: " + String.join(", ", candidates) + "?";
    }
}
