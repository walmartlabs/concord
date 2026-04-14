package com.walmartlabs.concord.server.security;

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

import java.util.Optional;

/**
 * Serializes one concrete principal type into a stable byte representation.
 *
 * <p>Implementations must treat the format as a compatibility contract: deserializers should continue to read
 * previously written data, fields should not be removed, renamed, reordered in ordered encodings, or change meaning
 * without a migration path, and newly added fields should be optional or have safe defaults.
 */
public interface PrincipalSerializer<T> {

    Class<T> principalType();

    String type();

    default boolean supports(Object principal) {
        return principalType().isInstance(principal);
    }

    default Optional<String> realmName(T principal) {
        return Optional.empty();
    }

    byte[] serialize(T principal);

    T deserialize(byte[] data) throws Exception;
}
