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
import java.nio.file.Path;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface SecretRequest {

    String org();

    String name();

    @Value.Default
    default boolean generatePassword() {
        return false;
    }

    @Nullable
    String storePassword();

    @Nullable
    SecretEntry.VisibilityEnum visibility();

    @Nullable
    String project();

    @Nullable
    Path data();

    @Nullable
    KeyPair keyPair();

    @Nullable
    UsernamePassword usernamePassword();

    static ImmutableSecretRequest.Builder builder() {
        return ImmutableSecretRequest.builder();
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface KeyPair {

        long serialVersionUID = 1L;

        Path privateKey();

        Path publicKey();

        static ImmutableKeyPair.Builder builder() {
            return ImmutableKeyPair.builder();
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface UsernamePassword {

        long serialVersionUID = 1L;

        String username();

        String password();

        static SecretRequest.UsernamePassword of(String username, String password) {
            return ImmutableUsernamePassword.builder()
                    .username(username)
                    .password(password)
                    .build();
        }
    }
}
