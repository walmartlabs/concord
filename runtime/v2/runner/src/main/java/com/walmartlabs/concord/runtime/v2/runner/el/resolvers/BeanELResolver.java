package com.walmartlabs.concord.runtime.v2.runner.el.resolvers;

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

import com.sun.el.util.ReflectionUtil;
import com.walmartlabs.concord.runtime.v2.runner.el.MethodNotFoundException;
import com.walmartlabs.concord.runtime.v2.sdk.CustomBeanELResolver;

import javax.el.ELContext;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Same as {@link javax.el.BeanELResolver}, but throws more detailed "method is not found" exceptions.
 */
public class BeanELResolver extends javax.el.BeanELResolver {

    private final List<CustomBeanELResolver> customResolvers;

    public BeanELResolver(List<CustomBeanELResolver> customResolvers) {
        this.customResolvers = customResolvers;
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        if (base == null || method == null) {
            return null; // same as original javax.el.BeanELResolver
        }

        try {
            var customResult = fromCustomResolvers(base, method, params);
            if (customResult != null) {
                if (customResult.base() == null && customResult.method() == null) {
                    context.setPropertyResolved(base, method);
                    return customResult.value();
                } else {
                    base = requireNonNull(customResult.base());
                    method = requireNonNull(customResult.method());
                }
            }

            var result = super.invoke(context, base, method, paramTypes, params);

            if (context.isPropertyResolved()) {
                Method m = ReflectionUtil.findMethod(base.getClass(), method.toString(), paramTypes, params);
                SensitiveDataProcessor.process(result, m);
            }

            return result;
        } catch (javax.el.MethodNotFoundException e) {
            throw new MethodNotFoundException(base, method, paramTypes);
        }
    }

    private CustomBeanELResolver.Result fromCustomResolvers(Object base, Object method, Object[] params) {
        return customResolvers.stream()
                .map(resolver -> resolver.invoke(base, method.toString(), params))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
