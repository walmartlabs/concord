package com.walmartlabs.concord.server.org.secret;

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

import com.walmartlabs.concord.sdk.Secret;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

@Value.Immutable
public interface SecretUpdateParams extends Serializable {

    @Nullable
    UUID newOrgId();

    @Nullable
    String newOrgName();

    @Nullable
    Set<UUID> newProjectIds();

    @Nullable
    @Deprecated
    UUID newProjectId();

    @Nullable
    @Deprecated
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
    Secret newSecret();

    @Nullable
    String newName();

    @Nullable
    SecretVisibility newVisibility();

    static ImmutableSecretUpdateParams.Builder builder() {
        return ImmutableSecretUpdateParams.builder();
    }
}
