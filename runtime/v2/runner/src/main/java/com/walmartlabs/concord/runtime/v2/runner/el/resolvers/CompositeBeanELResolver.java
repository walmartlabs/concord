package com.walmartlabs.concord.runtime.v2.runner.el.resolvers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.CustomBeanMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.Invocation;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.el.ELContext;
import java.util.List;
import java.util.Objects;

public class CompositeBeanELResolver extends javax.el.BeanELResolver {

    private final List<CustomTaskMethodResolver> customTaskMethodResolvers;
    private final List<CustomBeanMethodResolver> customBeanMethodResolvers;
    private final BeanELResolver defaultResolver;
    private final SensitiveDataProcessor sensitiveDataProcessor;

    public CompositeBeanELResolver(
            List<CustomTaskMethodResolver> customTaskMethodResolvers,
            List<CustomBeanMethodResolver> customBeanMethodResolvers,
            SensitiveDataProcessor sensitiveDataProcessor) {
        this.customTaskMethodResolvers = customTaskMethodResolvers;
        this.customBeanMethodResolvers = customBeanMethodResolvers;
        this.defaultResolver = new BeanELResolver(sensitiveDataProcessor);
        this.sensitiveDataProcessor = sensitiveDataProcessor;
    }

    @Override
    public Object invoke(ELContext elContext, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        if (base == null || method == null) {
            return null;
        }

        var invocation = findInvocation(base, method, paramTypes, params);
        if (invocation != null) {
            elContext.setPropertyResolved(base, method);
            return invocation.invoke(new DefaultInvocationContext(elContext, sensitiveDataProcessor));
        }

        return defaultResolver.invoke(elContext, base, method, paramTypes, params);
    }

    private Invocation findInvocation(Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        if (base instanceof Task task) {
            var invocation = customTaskMethodResolvers.stream()
                    .map(resolver -> resolver.resolve(task, method.toString(), paramTypes, params))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (invocation != null) {
                return invocation;
            }
        }

        return customBeanMethodResolvers.stream()
                .map(resolver -> resolver.resolve(base, method.toString(), paramTypes, params))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
