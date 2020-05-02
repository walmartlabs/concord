package com.walmartlabs.concord.cli.runner;

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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.SecretService;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class CliSecretServiceV1 implements SecretService {

    private final CliSecretService delegate;

    public CliSecretServiceV1(CliSecretService delegate) {
        this.delegate = delegate;
    }

    @Override
    public String exportAsString(Context ctx, String instanceId, String name, String password) throws Exception {
        return delegate.exportAsString(null, name, password);
    }

    @Override
    public String exportAsString(Context ctx, String instanceId, String orgName, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Map<String, String> exportKeyAsFile(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        return exportKeyAsFile(ctx, instanceId, workDir, null, name, password);
    }

    @Override
    public Map<String, String> exportKeyAsFile(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        com.walmartlabs.concord.runtime.v2.sdk.SecretService.KeyPair kp =
                delegate.exportKeyAsFile(Paths.get(workDir), orgName, name, password);

        Map<String, String> m = new HashMap<>();
        m.put("private", kp.privateKey().toString());
        m.put("public", kp.publicKey().toString());
        return m;
    }

    @Override
    public Map<String, String> exportCredentials(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Map<String, String> exportCredentials(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String exportAsFile(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String exportAsFile(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String decryptString(Context ctx, String instanceId, String s) throws Exception {
        return delegate.decryptString(s);
    }

    @Override
    public String encryptString(Context ctx, String instanceId, String orgName, String projectName, String value) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }
}
