package com.walmartlabs.concord.runtime.v2.sdk;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

/**
 * Task interface. All implementations should be annotated with {@code @Named}
 * using process-wide unique names.
 */
public interface Task {

    /**
     * This method is called when the task is invoked using the {@code task} syntax.
     */
    default TaskResult execute(Variables input) throws Exception {
        throw new IllegalStateException("The task doesn't support full task syntax yet. " +
                "Please call the task using expressions.");
    }
}
