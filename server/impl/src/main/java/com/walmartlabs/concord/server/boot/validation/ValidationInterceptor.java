package com.walmartlabs.concord.server.boot.validation;

/*
 * Copyright (c) 2007-2014 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
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

import com.walmartlabs.concord.server.sdk.validation.Validate;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Based on the original {@link org.sonatype.siesta.server.validation.ValidationInterceptor}.
 */
public class ValidationInterceptor implements MethodInterceptor {

    @Inject
    private ExecutableValidator methodValidator;

    public Object invoke(final MethodInvocation mi) throws Throwable {
        checkNotNull(methodValidator);

        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            final Validate validate = mi.getMethod().getAnnotation(Validate.class);

            validateParameters(mi.getThis(), mi.getMethod(), mi.getArguments(), validate.groups());

            final Object result = mi.proceed();

            validateReturnValue(mi.getThis(), mi.getMethod(), result, validate.groups());

            return result;
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    private void validateParameters(final Object obj, final Method method, final Object[] args, final Class<?>[] groups) {
        final Set<ConstraintViolation<Object>> violations = methodValidator.validateParameters(obj, method, args, groups);
        if (!violations.isEmpty()) {
            final String message = "Invalid arguments calling '" + method + "' with " + Arrays.deepToString(args);
            throw new ConstraintViolationException(message, violations);
        }
    }

    private void validateReturnValue(final Object obj, final Method method, final Object value, final Class<?>[] groups) {
        final Set<ConstraintViolation<Object>> violations = methodValidator.validateReturnValue(obj, method, value, groups);
        if (!violations.isEmpty()) {
            final String message = "Invalid value returned by '" + method + "' was " + value;
            throw new ConstraintViolationException(message, violations);
        }
    }
}
