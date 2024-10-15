package com.walmartlabs.concord.runtime.v2.runner.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import ch.qos.logback.classic.Level;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.UUID;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface LogContext extends Serializable {

   long serialVersionUID = 1L;

    @Nullable
    Long segmentId();

    String segmentName();

    UUID correlationId();

    boolean redirectSystemOutAndErr();

    @Value.Default
    default Level logLevel() {
        return Level.INFO;
    }

    @Value.Default
    default boolean duplicateToSystemSegment() {
        return false;
    }

    static ImmutableLogContext.Builder builder() {
        return ImmutableLogContext.builder();
    }
}
