package com.walmartlabs.concord.runtime.v2.runner.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
 * What a log segment represents in the process structure.
 */
public enum LogSegmentKind {

    /**
     * The whole execution of a step.
     */
    STEP,

    /** The whole execution of a loop, including retries and its error handler. */
    LOOP,

    /** The whole execution of a retried step without a loop, including its error handler. */
    RETRY,

    /**
     * One iteration of a loop.
     */
    ITERATION,

    /**
     * One attempt of a step with {@code retry}, including the first one.
     */
    ATTEMPT,

    /**
     * The {@code error} block of a step.
     */
    ERROR,

    /**
     * The whole execution of a {@code parallel} block.
     */
    PARALLEL
}
