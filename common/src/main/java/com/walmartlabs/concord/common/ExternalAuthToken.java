package com.walmartlabs.concord.common;

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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.OffsetDateTime;

@JsonDeserialize(as = ImmutableSimpleToken.class)
public interface ExternalAuthToken {

    @JsonProperty("token")
    String token();

    @Nullable
    @JsonProperty("username")
    String username();

    @Nullable
    @JsonProperty("expires_at")
    // GitHub gives time in seconds, but most parsers (e.g. jackson) expect milliseconds
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSS]X")
    OffsetDateTime expiresAt();

    @Value.Default
    @JsonIgnore
    default long secondsUntilExpiration() {
        if (expiresAt() == null) {
            return Long.MAX_VALUE;
        }

        var d = Duration.between(OffsetDateTime.now(), expiresAt());
        return d.getSeconds();
    }

    /**
     * Basic implementation of an expiring token.
     */
    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface SimpleToken extends ExternalAuthToken {
        static ImmutableSimpleToken.Builder builder() {
            return ImmutableSimpleToken.builder();
        }
    }

    /**
     * A token that effectively never expires.
     */
    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface StaticToken extends ExternalAuthToken {

        @Value.Default
        @Nullable
        @Override
        default OffsetDateTime expiresAt() {
            return null;
        }

        static ImmutableStaticToken.Builder builder() {
            return ImmutableStaticToken.builder();
        }
    }

}
