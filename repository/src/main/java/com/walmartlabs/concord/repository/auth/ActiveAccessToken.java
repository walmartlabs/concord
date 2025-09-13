package com.walmartlabs.concord.repository.auth;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.OffsetDateTime;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonDeserialize(as = ImmutableActiveAccessToken.class)
@JsonSerialize(as = ImmutableActiveAccessToken.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface ActiveAccessToken {

    @JsonProperty("token")
    String token();

    @Nullable
    @JsonProperty("expires_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSS]X")
    OffsetDateTime expiresAt();

    default long secondsUntilExpiration() {
        if (expiresAt() == null) {
            return Long.MAX_VALUE;
        }

        var d = Duration.between(OffsetDateTime.now(), expiresAt());
        return d.getSeconds();
    }

    static ImmutableActiveAccessToken.Builder builder() {
        return ImmutableActiveAccessToken.builder();
    }

}
