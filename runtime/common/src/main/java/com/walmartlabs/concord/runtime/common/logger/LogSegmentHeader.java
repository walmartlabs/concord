package com.walmartlabs.concord.runtime.common.logger;

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

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface LogSegmentHeader {

    int length();

    long segmentId();

    int warnCount();

    int errorCount();

    LogSegmentStatus status();

    static ImmutableLogSegmentHeader.Builder builder() {
        return ImmutableLogSegmentHeader.builder();
    }
}
