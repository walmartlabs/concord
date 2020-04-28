package com.walmartlabs.concord.plugins.ansible.v2;

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

import com.walmartlabs.concord.plugins.ansible.AnsibleSecretService;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;

import java.nio.file.Path;

public class SecretServiceV2 implements AnsibleSecretService {

    private final SecretService delegate;

    public SecretServiceV2(SecretService delegate) {
        this.delegate = delegate;
    }

    @Override
    public Path exportAsFile(String orgName, String secretName, String password)  throws Exception {
        return delegate.exportAsFile(orgName, secretName, password);
    }

    @Override
    public SecretService.UsernamePassword exportCredentials(String org, String name, String password) throws Exception {
        return delegate.exportCredentials(org, name, password);
    }

    @Override
    public SecretService.KeyPair exportKeyAsFile(String org, String name, String password) throws Exception {
        return delegate.exportKeyAsFile(org, name, password);
    }
}
