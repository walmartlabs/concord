package com.walmartlabs.concord.agent;

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

public interface JobInstance {

    /**
     * Wait for the job to finish.
     * Throws an {@link java.util.concurrent.ExecutionException} if the job finishes unsuccessfully.
     */
    void waitForCompletion() throws Exception;

    /**
     * Cancel the job (unless it's already cancelled or done).
     */
    void cancel();

    /**
     * Returns {@code true} if the job was cancelled using the {@link #cancel()} method.
     */
    boolean isCancelled();
}
