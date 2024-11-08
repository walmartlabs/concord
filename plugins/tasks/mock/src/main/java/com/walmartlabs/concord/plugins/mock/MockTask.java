package com.walmartlabs.concord.plugins.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.sdk.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class MockTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(MockTask.class);

    private final Context ctx;
    private final String taskName;
    private final MockDefinitionProvider mockDefinitionProvider;
    private final Class<? extends Task> originalTaskClass;
    private final Supplier<Task> delegate;

    public MockTask(Context ctx, String taskName,
                    MockDefinitionProvider mockDefinitionProvider,
                    Class<? extends Task> originalTaskClass,
                    Supplier<Task> delegate) {
        this.ctx = ctx;
        this.taskName = taskName;
        this.mockDefinitionProvider = mockDefinitionProvider;
        this.originalTaskClass = originalTaskClass;
        this.delegate = delegate;
    }

    @Override
    public TaskResult execute(Variables input) throws Exception{
        MockDefinition mockDefinition = mockDefinitionProvider.find(ctx, taskName, input);
        if (mockDefinition == null) {
            return delegate.get().execute(input);
        }

        log.info("The actual task is not being executed; this is a mock");

        if (mockDefinition.throwError() != null) {
            throw new UserDefinedException(mockDefinition.throwError());
        }

        boolean success = MapUtils.getBoolean(mockDefinition.out(), "ok", true);
        return TaskResult.of(success)
                .values(mockDefinition.out());
    }

    public Object call(CustomTaskMethodResolver.InvocationContext ic, String method, Class<?>[] paramTypes, Object[] params) {
        MockDefinition mockDefinition = mockDefinitionProvider.find(ctx, taskName, method, params);
        if (mockDefinition == null) {
            return ic.invoker().invoke(delegate.get(), method, paramTypes, params);
        }

        log.info("The actual '{}.{}()' is not being executed; this is a mock", taskName, method);

        if (mockDefinition.throwError() != null) {
            throw new UserDefinedException(mockDefinition.throwError());
        }

        return mockDefinition.result();
    }

    public String taskName() {
        return taskName;
    }

    public Class<? extends Task> originalTaskClass() {
        return originalTaskClass;
    }
}
