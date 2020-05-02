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

import com.walmartlabs.concord.runtime.v2.sdk.SecretService;

import java.nio.file.Path;

public class CliSecretServiceV2 implements SecretService {

    private final CliSecretService secretService;
    private final Path workDir;

    public CliSecretServiceV2(CliSecretService secretService, Path workDir) {
        this.secretService = secretService;
        this.workDir = workDir;
    }

    @Override
    public String exportAsString(String orgName, String name, String password) throws Exception {
        return secretService.exportAsString(orgName, name, password);
    }

    @Override
    public KeyPair exportKeyAsFile(String orgName, String name, String password) throws Exception {
        return secretService.exportKeyAsFile(workDir, orgName, name, password);
    }

    @Override
    public UsernamePassword exportCredentials(String orgName, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Path exportAsFile(String orgName, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String decryptString(String s) {
        return secretService.decryptString(s);
    }

    @Override
    public String encryptString(String orgName, String projectName, String value) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }
}
