package com.walmartlabs.concord.server.boot.validation;

/*
 * Hibernate Validator, declare and validate application constraints
 *
 * License: Apache License, Version 2.0
 * See the license.txt file in the root directory or <http://www.apache.org/licenses/LICENSE-2.0>.
 *
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

import org.hibernate.validator.spi.properties.ConstrainableExecutable;
import org.hibernate.validator.spi.properties.GetterPropertySelectionStrategy;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A version of the original org.hibernate.validator.internal.properties.DefaultGetterPropertySelectionStrategy
 * adapted for the project (mostly to make the "takari-lifecycle-plugin" happy).
 */
public class DefaultGetterPropertySelectionStrategy implements GetterPropertySelectionStrategy {

    private static final String GETTER_PREFIX_GET = "get";
    private static final String GETTER_PREFIX_IS = "is";
    private static final String GETTER_PREFIX_HAS = "has";
    private static final String[] GETTER_PREFIXES = {
            GETTER_PREFIX_GET,
            GETTER_PREFIX_IS,
            GETTER_PREFIX_HAS
    };

    @Override
    public Optional<String> getProperty(ConstrainableExecutable executable) {
        if (!isGetter(executable)) {
            return Optional.empty();
        }

        String methodName = executable.getName();

        for (String prefix : GETTER_PREFIXES) {
            if (methodName.startsWith(prefix)) {
                return Optional.of(methodName.substring(prefix.length()).toLowerCase());
            }
        }

        throw new AssertionError("Method " + executable.getName() + " was considered a getter but we couldn't extract a property name.");
    }

    @Override
    public Set<String> getGetterMethodNameCandidates(String propertyName) {

        Set<String> nameCandidates = new HashSet<>(GETTER_PREFIXES.length);
        for (String prefix : GETTER_PREFIXES) {
            nameCandidates.add(prefix + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1));
        }
        return nameCandidates;
    }

    /**
     * Checks whether the given executable is a valid JavaBean getter method, which
     * is the case if
     * <ul>
     * <li>its name starts with "get" and it has a return type but no parameter or</li>
     * <li>its name starts with "is", it has no parameter and is returning
     * {@code boolean} or</li>
     * <li>its name starts with "has", it has no parameter and is returning
     * {@code boolean} (HV-specific, not mandated by the JavaBeans spec).</li>
     * </ul>
     *
     * @param executable The executable of interest.
     * @return {@code true}, if the given executable is a JavaBean getter method,
     * {@code false} otherwise.
     */
    private static boolean isGetter(ConstrainableExecutable executable) {
        if (executable.getParameterTypes().length != 0) {
            return false;
        }

        String methodName = executable.getName();

        //<PropertyType> get<PropertyName>()
        if (methodName.startsWith(GETTER_PREFIX_GET) && executable.getReturnType() != void.class) {
            return true;
        }
        //boolean is<PropertyName>()
        else if (methodName.startsWith(GETTER_PREFIX_IS) && executable.getReturnType() == boolean.class) {
            return true;
        }
        //boolean has<PropertyName>()
        else if (methodName.startsWith(GETTER_PREFIX_HAS) && executable.getReturnType() == boolean.class) {
            return true;
        }

        return false;
    }
}
