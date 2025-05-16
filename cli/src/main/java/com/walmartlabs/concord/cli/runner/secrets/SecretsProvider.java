package com.walmartlabs.concord.cli.runner.secrets;

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

import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService.KeyPair;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService.SecretCreationResult;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService.UsernamePassword;

import java.nio.file.Path;
import java.util.Optional;

public interface SecretsProvider {

    Optional<KeyPair> exportKeyAsFile(String orgName, String secretName, String password) throws Exception;

    Optional<String> exportAsString(String orgName, String secretName, String password) throws Exception;

    Optional<Path> exportAsFile(String orgName, String secretName, String password) throws Exception;

    Optional<UsernamePassword> exportCredentials(String orgName, String secretName, String secretPassword) throws Exception;

    SecretCreationResult createKeyPair(SecretService.SecretParams secret, KeyPair keyPair) throws Exception;

    SecretCreationResult createUsernamePassword(SecretService.SecretParams secret, UsernamePassword usernamePassword) throws Exception;

    SecretCreationResult createData(SecretService.SecretParams secret, byte[] data) throws Exception;
}
