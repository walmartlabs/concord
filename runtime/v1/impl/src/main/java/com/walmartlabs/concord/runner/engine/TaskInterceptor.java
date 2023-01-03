package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.sdk.Context;
import io.takari.bpm.api.ExecutionException;

/**
 * Activates before and after task call.
 */
public interface TaskInterceptor {

    /**
     * Activates before the task call.
     *
     * @param taskName name of the called task
     * @param instance the task's instance that is being called
     * @param ctx      the current process context
     */
    void preTask(String taskName, Object instance, Context ctx) throws ExecutionException;

    /**
     * Activates after the task call.
     *
     * @param taskName name of the called task
     * @param instance the task's instance that was being called
     * @param ctx      the current process context
     */
    void postTask(String taskName, Object instance, Context ctx) throws ExecutionException;
}
