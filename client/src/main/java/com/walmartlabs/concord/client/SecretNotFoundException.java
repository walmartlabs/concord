package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

public class SecretNotFoundException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String orgName;

    private final String secretName;

    public SecretNotFoundException(String orgName, String secretName) {
        super("Secret not found: " + orgName + "/" + secretName);
        this.orgName = orgName;
        this.secretName = secretName;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getSecretName() {
        return secretName;
    }
}
