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

import javax.el.ELContext;

/**
 * Same as {@link javax.el.BeanELResolver}, but throws more detailed "method is not found" exceptions.
 */
public class BeanELResolver extends javax.el.BeanELResolver {

    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        try {
            return super.invoke(context, base, method, paramTypes, params);
        } catch (javax.el.MethodNotFoundException e) {
            throw new MethodNotFoundException(base, method);
        }
    }
}
