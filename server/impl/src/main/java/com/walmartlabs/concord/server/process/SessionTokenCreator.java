package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.common.secret.SecretUtils;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;
import java.util.Base64;
import java.util.UUID;

public class SessionTokenCreator {

    private final SecretStoreConfiguration secretCfg;

    @Inject
    public SessionTokenCreator(SecretStoreConfiguration secretCfg) {
        this.secretCfg = secretCfg;
    }

    public String create(ProcessKey processKey) {
        byte[] salt = secretCfg.getSecretStoreSalt();
        byte[] pwd = secretCfg.getServerPwd();

        UUID instanceId = processKey.getInstanceId();

        byte[] ab = SecretUtils.encrypt(instanceId.toString().getBytes(), pwd, salt);
        return Base64.getEncoder().encodeToString(ab);
    }
}
