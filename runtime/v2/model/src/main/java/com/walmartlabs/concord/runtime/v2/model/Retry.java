package com.walmartlabs.concord.runtime.v2.model;

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

import com.walmartlabs.concord.common.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.Duration;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface Retry extends Serializable {

    long serialVersionUID = 1L;

    int DEFAULT_RETRY_TIMES = 1;

    Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(5);

    @Value.Default
    default int times() {
        return DEFAULT_RETRY_TIMES;
    }

    @Value.Default
    default Duration delay() {
        return DEFAULT_RETRY_DELAY;
    }

    @Nullable
    String timesExpression();

    @Nullable
    String delayExpression();

    @Nullable
    @AllowNulls
    Map<String, Serializable> input();

    static ImmutableRetry.Builder builder() {
        return ImmutableRetry.builder();
    }
}
