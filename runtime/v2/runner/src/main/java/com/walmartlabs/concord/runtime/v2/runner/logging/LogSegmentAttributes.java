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
 * @param parentId the segment open in the thread when the segment starts
 * @param kind     what the segment represents
 * @param index    number of the iteration or the attempt (starting from 0)
 * @param threadId ID of the runtime thread, {@code null} for the main thread
 */
public record LogSegmentAttributes(@Nullable Long parentId,
                                   LogSegmentKind kind,
                                   @Nullable Integer index,
                                   @Nullable Integer threadId) {
}
