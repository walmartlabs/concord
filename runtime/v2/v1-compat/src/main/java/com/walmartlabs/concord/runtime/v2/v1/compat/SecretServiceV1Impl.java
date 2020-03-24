package com.walmartlabs.concord.runtime.v2.v1.compat;

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

import com.google.inject.Inject;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.SecretService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SecretServiceV1Impl implements SecretService {

    private final com.walmartlabs.concord.runtime.v2.sdk.SecretService delegate;

    @Inject
    public SecretServiceV1Impl(com.walmartlabs.concord.runtime.v2.sdk.SecretService delegate) {
        this.delegate = delegate;
    }

    @Override
    public String exportAsString(Context ctx, String instanceId, String name, String password) throws Exception {
        return exportAsString(ctx, instanceId, null, name, password);
    }

    @Override
    public String exportAsString(Context ctx, String instanceId, String orgName, String name, String password) throws Exception {
        return delegate.exportAsString(assertOrgName(ctx, orgName), name, password);
    }

    @Override
    public Map<String, String> exportKeyAsFile(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        return exportKeyAsFile(ctx, instanceId, workDir, null, name, password);
    }

    @Override
    public Map<String, String> exportKeyAsFile(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        com.walmartlabs.concord.runtime.v2.sdk.SecretService.KeyPair keys = delegate.exportKeyAsFile(assertOrgName(ctx, orgName), name, password);

        Path baseDir = Paths.get(workDir);

        Map<String, String> m = new HashMap<>();
        m.put("private", baseDir.relativize(keys.privateKey()).toString());
        m.put("public", baseDir.relativize(keys.publicKey()).toString());
        return m;
    }

    @Override
    public Map<String, String> exportCredentials(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        return exportCredentials(ctx, instanceId, workDir, null, name, password);
    }

    @Override
    public Map<String, String> exportCredentials(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        com.walmartlabs.concord.runtime.v2.sdk.SecretService.UsernamePassword cred = delegate.exportCredentials(assertOrgName(ctx, orgName), name, password);

        Map<String, String> m = new HashMap<>();
        m.put("username", cred.username());
        m.put("password", cred.password());
        return m;
    }

    @Override
    public String exportAsFile(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        return exportAsFile(ctx, instanceId, workDir, null, name, password);
    }

    @Override
    public String exportAsFile(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        Path baseDir = Paths.get(workDir);

        Path p = delegate.exportAsFile(assertOrgName(ctx, orgName), name, password);
        return baseDir.relativize(p).toString();
    }

    @Override
    public String decryptString(Context ctx, String instanceId, String s) throws Exception {
        return delegate.decryptString(s);
    }

    @Override
    public String encryptString(Context ctx, String instanceId, String orgName, String projectName, String value) throws Exception {
        return delegate.encryptString(orgName, projectName, value);
    }

    @SuppressWarnings("unchecked")
    private String assertOrgName(Context ctx, String orgName) {
        if (orgName != null) {
            return orgName;
        }

        Map<String, Object> pi = (Map<String, Object>) ctx.getVariable(Constants.Request.PROJECT_INFO_KEY);
        return Optional.ofNullable(pi)
                .map(p -> (String) p.get("orgName"))
                .orElseThrow(() -> new IllegalArgumentException("Organization name not specified"));
    }
}
