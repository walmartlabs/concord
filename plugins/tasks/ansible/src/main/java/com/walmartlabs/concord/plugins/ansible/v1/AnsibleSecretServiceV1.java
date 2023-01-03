package com.walmartlabs.concord.plugins.ansible.v1;

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

import com.walmartlabs.concord.plugins.ansible.secrets.AnsibleSecretService;
import com.walmartlabs.concord.plugins.ansible.secrets.KeyPair;
import com.walmartlabs.concord.plugins.ansible.secrets.UsernamePassword;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.SecretService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class AnsibleSecretServiceV1 implements AnsibleSecretService {

    private final Context ctx;
    private final SecretService delegate;

    public AnsibleSecretServiceV1(Context ctx, SecretService delegate) {
        this.ctx = ctx;
        this.delegate = delegate;
    }

    @Override
    public Path exportAsFile(String orgName, String secretName, String password) throws Exception {
        String instanceId = ContextUtils.assertString(ctx, Constants.Context.TX_ID_KEY);
        String workDir = ContextUtils.assertString(ctx, Constants.Context.WORK_DIR_KEY);
        String result = delegate.exportAsFile(ctx, instanceId, workDir, orgName, secretName, password);
        return Paths.get(workDir, result);
    }

    @Override
    public UsernamePassword exportCredentials(String orgName, String secretName, String password) throws Exception {
        String instanceId = ContextUtils.assertString(ctx, Constants.Context.TX_ID_KEY);
        String workDir = ContextUtils.assertString(ctx, Constants.Context.WORK_DIR_KEY);
        Map<String, String> result = delegate.exportCredentials(ctx, instanceId, workDir, orgName, secretName, password);
        return new UsernamePassword(result.get("username"), result.get("password"));
    }

    @Override
    public KeyPair exportKeyAsFile(String orgName, String secretName, String password) throws Exception {
        String instanceId = ContextUtils.assertString(ctx, Constants.Context.TX_ID_KEY);
        String workDir = ContextUtils.assertString(ctx, Constants.Context.WORK_DIR_KEY);
        Map<String, String> result = delegate.exportKeyAsFile(ctx, instanceId, workDir, orgName, secretName, password);
        return new KeyPair(Paths.get(workDir, result.get("private")), Paths.get(workDir, result.get("public")));
    }

}
