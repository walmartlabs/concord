package com.walmartlabs.concord.client;

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


import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.UUID;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface UpdateSecretRequest {

    @Nullable
    UUID newOrgId();

    @Nullable
    String newOrgName();

    @Nullable
    UUID newProjectId();

    @Nullable
    String newProjectName();

    @Value.Default
    default boolean removeProjectLink() {
        return false;
    }

    @Nullable
    UUID newOwnerId();

    @Nullable
    String currentPassword();

    @Nullable
    String newPassword();

    @Nullable
    String newName();

    @Nullable
    SecretEntry.VisibilityEnum newVisibility();

    @Nullable
    byte[] data();

    @Nullable
    CreateSecretRequest.KeyPair keyPair();

    @Nullable
    CreateSecretRequest.UsernamePassword usernamePassword();

    static ImmutableUpdateSecretRequest.Builder builder() {
        return ImmutableUpdateSecretRequest.builder();
    }
}
