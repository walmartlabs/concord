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

import com.walmartlabs.concord.common.Matcher;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.svm.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(MockTask.class);

    private final State state;
    private final MockDefinition mockDefinition;

    public MockTask(State state, MockDefinition mockDefinition) {
        this.state = state;
        this.mockDefinition = mockDefinition;
    }

    @Override
    public TaskResult execute(Variables input) {
        if (!Matcher.matches(input.toMap(), mockDefinition.in())) {
            throw new UserDefinedException("Input variables for '" + mockDefinition.name() + "' not matched with actual input params: " + input.toMap());
        }

        if (mockDefinition.inputStoreId() != null) {
            MockInputUtils.storeInput(state, mockDefinition.inputStoreId(), input.toMap());
        }

        log.info("The actual task is not being executed; this is a mock");

        if (mockDefinition.throwError() != null) {
            throw new UserDefinedException(mockDefinition.throwError());
        }

        boolean success = MapUtils.getBoolean(mockDefinition.out(), "ok", true);
        return TaskResult.of(success)
                .values(mockDefinition.out());
    }
}
