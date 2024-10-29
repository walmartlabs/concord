package com.walmartlabs.concord.plugins.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.CustomBeanELResolver;

public class MockBeanELResolver implements CustomBeanELResolver {

    @Override
    public Result invoke(Object base, String method, Object[] params) {
        if (base instanceof MockTask mockTask) {
            return mockTask.call(method, params);
        }
        return null;
//
//        if (base != null && "com.walmartlabs.concord.plugins.mock.VerifyTask$Mock".equals(base.getClass().getName())) {
//            paramTypes = new Class[2];
//            paramTypes[0] = String.class;
//            paramTypes[1] = List.class;
//
//            Object[] newParams = new Object[2];
//            newParams[0] = method;
//            newParams[1] = Arrays.asList(params);
//            method = "verify";
//            params = newParams;
//        }
    }
}
