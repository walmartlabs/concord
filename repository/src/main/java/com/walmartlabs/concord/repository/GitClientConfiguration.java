package com.walmartlabs.concord.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface GitClientConfiguration {

    Optional<String> oauthToken();

    Optional<String> oauthUsername();

    Optional<String> oauthUrlPattern();

    @Value.Default
    default Set<String> allowedSchemes() {
        return Set.of("https", "http", "ssh", "classpath");
    }

    @Value.Default
    default Duration defaultOperationTimeout() {
        return Duration.ofMinutes(10L);
    }

    @Value.Default
    default Duration fetchTimeout() {
        return Duration.ofMinutes(10L);
    }

    @Value.Default
    default int httpLowSpeedLimit() {
        return 0;
    }

    @Value.Default
    default Duration httpLowSpeedTime() {
        return Duration.ofMinutes(0L);
    }

    @Value.Default
    default Duration sshTimeout() {
        return Duration.ofMinutes(10);
    }

    @Value.Default
    default int sshTimeoutRetryCount() {
        return 1;
    }

    static ImmutableGitClientConfiguration.Builder builder() {
        return ImmutableGitClientConfiguration.builder();
    }
}
