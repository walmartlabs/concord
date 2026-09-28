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

import javax.annotation.Nullable;

/**
 * Where a log segment belongs in the process structure.
 *
 * @param parentId      the enclosing segment (or the failed step for error handlers)
 * @param attempt       number of the attempt (starting from 1) of a step with {@code retry}
 * @param loopIndex     index of the loop iteration (starting from 0)
 * @param errorHandler  {@code true} if the segment is a part of an {@code error} block
 * @param threadId      ID of the runtime thread, {@code null} for the main thread
 */
public record LogSegmentAttributes(@Nullable Long parentId,
                                   @Nullable Integer attempt,
                                   @Nullable Integer loopIndex,
                                   boolean errorHandler,
                                   @Nullable Integer threadId) {

    public static final LogSegmentAttributes NONE = new LogSegmentAttributes(null, null, null, false, null);
}
