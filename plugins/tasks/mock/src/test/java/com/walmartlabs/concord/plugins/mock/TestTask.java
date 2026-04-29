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

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Named;
import java.util.List;
import java.util.Map;

@Named("testTask")
public class TestTask implements Task {

    @Override
    public TaskResult execute(Variables input) {
        return TaskResult.success();
    }

    public String doAction(String input) {
        return input;
    }

    public void myMethod(int a, String b, boolean c, List<Integer> d, Map<String, Object> e) {
        throw new RuntimeException("myMethod not implemented");
    }

    public void myMethod(int a, String b) {
        throw new RuntimeException("myMethod not implemented");
    }
}
