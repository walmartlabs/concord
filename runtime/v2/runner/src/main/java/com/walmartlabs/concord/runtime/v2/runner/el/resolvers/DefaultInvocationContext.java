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

import com.walmartlabs.concord.runtime.v2.sdk.InvocationContext;
import com.walmartlabs.concord.runtime.v2.sdk.MethodInvoker;

import javax.el.ELContext;

public class DefaultInvocationContext implements InvocationContext {

    private final ELContext elContext;
    private final javax.el.BeanELResolver beanELResolver;

    public DefaultInvocationContext(ELContext elContext) {
        this.elContext = elContext;
        this.beanELResolver = new BeanELResolver();
    }

    @Override
    public MethodInvoker invoker() {
        return (base, method, paramTypes, params) -> beanELResolver.invoke(elContext, base, method, paramTypes, params);
    }
}
