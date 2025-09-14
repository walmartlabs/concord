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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonDeserialize(as = ImmutableAppInstallation.class)
public interface AppInstallation extends GitAuth {

    String apiUrl();

    String clientId();

    @Nullable
    Path privateKey();

    @Nullable
    String privateKeyData();

    @Value.Derived
    default String pkData() {
        if (privateKeyData() != null) {
            return privateKeyData();
        }

        var pk = privateKey();
        if (pk == null) {
            throw new IllegalStateException("Either 'privateKey' or 'privateKeyData' must be provided");
        }

        try {
            return Files.readString(pk);
        } catch (IOException e) {
            throw new RuntimeException("Error reading the private key file: " + pk, e);
        }
    }

    /**
     * Validates object state before returned to caller.
     */
    @Value.Check
    default void check() {
        if (privateKey() == null && privateKeyData() == null) {
            throw new IllegalStateException("Either 'privateKey' or 'privateKeyData' must be provided");
        }
    }


    static ImmutableAppInstallation.Builder builder() {
        return ImmutableAppInstallation.builder();
    }
}
