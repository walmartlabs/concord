package com.walmartlabs.concord.server.plugins.oidc;

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

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public record VerifiedProfile(UserProfile profile, String issuer, Instant observedAt, Instant expiresAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public VerifiedProfile {
        requireNonNull(profile);
        requireNonNull(issuer);
        requireNonNull(observedAt);
        requireNonNull(expiresAt);
        profile = new UserProfile(profile.id(), profile.email(), profile.displayName(), null,
                sanitizeMap(profile.attributes(), profile.accessToken()));
    }

    private static Map<String, Object> sanitizeMap(Map<String, Object> attributes, String accessToken) {
        if (attributes == null) {
            return null;
        }
        var result = new LinkedHashMap<String, Object>();
        attributes.forEach((key, value) -> {
            var normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            if (!normalized.contains("token") && !normalized.contains("credential") &&
                    !normalized.equals("authorization") && !normalized.equals("password") &&
                    !normalized.equals("clientsecret") &&
                    (accessToken == null || !accessToken.equals(value))) {
                result.put(key, sanitizeValue(value, accessToken));
            }
        });
        return result;
    }

    private static Object sanitizeValue(Object value, String accessToken) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, nested) -> result.put((String) key, nested));
            return sanitizeMap(result, accessToken);
        }
        if (value instanceof Iterable<?> values) {
            var result = new ArrayList<>();
            for (var nested : values) {
                if (accessToken == null || !accessToken.equals(nested)) {
                    result.add(sanitizeValue(nested, accessToken));
                }
            }
            return result;
        }
        return value;
    }
}
