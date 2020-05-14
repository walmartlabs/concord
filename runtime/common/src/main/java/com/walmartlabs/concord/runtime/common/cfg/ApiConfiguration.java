package com.walmartlabs.concord.runtime.common.cfg;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.concurrent.TimeUnit;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableApiConfiguration.class)
@JsonDeserialize(as = ImmutableApiConfiguration.class)
public interface ApiConfiguration {

    /**
     * Base URL of the server API.
     */
    @Value.Default
    default String baseUrl() {
        return "http://localhost:8001";
    }

    /**
     * Connection timeout (ms)
     */
    @Value.Default
    default int connectTimeout() {
        return 10000;
    }

    /**
     * Socket read timeout (ms)
     */
    @Value.Default
    default int readTimeout() {
        return 60000;
    }

    /**
     * Socket write timeout (ms)
     */
    @Value.Default
    default int writeTimeout() {
        return 30000;
    }

    /**
     * Number of retries if an API call fails.
     */
    @Value.Default
    default int retryCount() {
        return 3;
    }

    /**
     * Delay (in ms) between retries.
     *
     * @see #retryCount
     */
    @Value.Default
    default int retryInterval() {
        return 5000;
    }

    /**
     * Max interval (in ms) without heartbeat before the process fails.
     */
    @Value.Default
    default long maxNoHeartbeatInterval() {
        return TimeUnit.MINUTES.toMillis(5);
    }

    static ImmutableApiConfiguration.Builder builder() {
        return ImmutableApiConfiguration.builder();
    }
}
