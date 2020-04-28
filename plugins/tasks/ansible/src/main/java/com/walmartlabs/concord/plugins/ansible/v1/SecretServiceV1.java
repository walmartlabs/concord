package com.walmartlabs.concord.plugins.ansible.v1;

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
import com.walmartlabs.concord.sdk.Context;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class SecretServiceV1 implements AnsibleSecretService {

    private final com.walmartlabs.concord.sdk.SecretService delegate;
    private final Context context;
    private final String instanceId;
    private final String workDir;

    public SecretServiceV1(com.walmartlabs.concord.sdk.SecretService delegate, Context context, String instanceId, String workDir) {
        this.delegate = delegate;
        this.context = context;
        this.instanceId = instanceId.toString();
        this.workDir = workDir;
    }

    @Override
    public Path exportAsFile(String orgName, String secretName, String password)  throws Exception {
        return Paths.get(delegate.exportAsFile(context, instanceId, workDir, orgName, secretName, password));
    }

    @Override
    public SecretService.UsernamePassword exportCredentials(String org, String name, String password) throws Exception {
        Map<String, String> result = delegate.exportCredentials(context, instanceId, workDir, org, name, password);
        return SecretService.UsernamePassword.of(result.get("username"), result.get("password"));
    }

    @Override
    public SecretService.KeyPair exportKeyAsFile(String org, String name, String password) throws Exception {
        Map<String, String> result = delegate.exportKeyAsFile(context, instanceId, workDir, org, name, password);
        return SecretService.KeyPair.builder()
                .publicKey(Paths.get(result.get("public")))
                .privateKey(Paths.get(result.get("private")))
                .build();
    }
}
