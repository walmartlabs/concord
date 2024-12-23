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

import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

public class MockTaskMethodResolver implements CustomTaskMethodResolver {

    @Override
    public Invocation resolve(Task base, String method, Class<?>[] paramTypes, Object[] params) {
        if (base instanceof MockTask mockTask) {
            return new Invocation() {

                @Override
                public String taskName() {
                    return mockTask.taskName();
                }

                @Override
                public Class<? extends Task> taskClass() {
                    return mockTask.originalTaskClass();
                }

                @Override
                public Object invoke(InvocationContext context) {
                    return mockTask.call(context, method, paramTypes, params);
                }
            };
        }

        return null;
    }
}
