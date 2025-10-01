package com.walmartlabs.concord.github.appinstallation;

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

import org.immutables.value.Value;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.util.Objects;

@Value.Immutable
@Value.Style(jdkOnly = true, redactedMask = "**redacted**")
interface CacheKey {

    URI repoUri();

    @Nullable
    @Value.Redacted
    byte[] binaryDataSecret();

    @Value.Default
    default int weight() {
        var weight = 1;

        if (binaryDataSecret() != null) {
            var data = Objects.requireNonNull(binaryDataSecret());
            weight += 1;
            weight += data.length / 1024;
        }

        return weight;
    }

    static CacheKey from(URI repoUri) {
        return ImmutableCacheKey.builder()
                .repoUri(repoUri)
                .build();
    }

    static CacheKey from(URI repoUri, @Nonnull byte[] secret) {
        return ImmutableCacheKey.builder()
                .repoUri(repoUri)
                .binaryDataSecret(secret)
                .build();
    }

}
