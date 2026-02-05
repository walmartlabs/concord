package com.walmartlabs.concord.it.runtime.v2;

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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.NewSecretQuery;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;

import static com.walmartlabs.concord.it.common.ITUtils.randomPwd;
import static com.walmartlabs.concord.it.common.ITUtils.randomString;

@ExtendWith(SharedConcordExtension.class)
public class CryptoIT extends AbstractTest {

    static ConcordRule concord;

    @BeforeAll
    static void setUp(ConcordRule rule) {
        concord = rule;
    }

    /**
     * Tests various methods of the 'crypto' plugin.
     */
    @Test
    public void test() throws Exception {
        ApiClient apiClient = concord.apiClient();

        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(apiClient);
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        ProjectsApi projectsApi = new ProjectsApi(apiClient);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.OWNERS));

        // ---

        String mySecretPwd = "pwd_" + randomPwd();

        String myStringSecretName = "secret_" + randomString();
        String myStringSecretValue = "value_" + randomString();
        concord.secrets().createSecret(NewSecretQuery.builder()
                        .org(orgName)
                        .name(myStringSecretName)
                        .storePassword(mySecretPwd)
                        .build(),
                myStringSecretValue.getBytes());

        String myKeypairName = "secret_" + randomString();
        concord.secrets().generateKeyPair(NewSecretQuery.builder()
                .org(orgName)
                .name(myKeypairName)
                .storePassword(mySecretPwd)
                .build());

        String myCredentialsName = "secret_" + randomString();
        String myUsername = "username_" + randomString();
        String myPassword = "password_" + randomPwd();
        concord.secrets().createSecret(NewSecretQuery.builder()
                        .org(orgName)
                        .name(myCredentialsName)
                        .storePassword(mySecretPwd).build(),
                myUsername, myPassword);

        String mySecretFileName = "secret_" + randomString();
        String mySecretFileValue = "value_" + randomString();
        concord.secrets().createSecret(NewSecretQuery.builder()
                        .org(orgName)
                        .name(mySecretFileName)
                        .storePassword(mySecretPwd)
                        .build(),
                mySecretFileValue.getBytes());

        String myRawString = "raw_" + randomString();

        // ---

        Payload payload = new Payload()
                .org(orgName)
                .project(projectName)
                .archive(resource("crypto"))
                .arg("myOrg", orgName)
                .arg("mySecretPwd", mySecretPwd)
                .arg("myStringSecret", myStringSecretName)
                .arg("myKeypair", myKeypairName)
                .arg("myCredentials", myCredentialsName)
                .arg("mySecretFile", mySecretFileName)
                .arg("myRawString", myRawString);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*String: " + myStringSecretValue.replaceAll(".", "$0 ") + ".*");
        proc.assertLog(".*Keypair: \\{.*private.*}.*");
        proc.assertLog(".*Credentials: .*" + myPassword.replaceAll(".", "$0 ") + ".*");
        proc.assertLog(".*File: .*");
        proc.assertLog(".*Encrypted string: " + myRawString.replaceAll(".", "$0 ") + ".*");
    }

    @Test
    public void testCreate() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(concord.apiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        Payload payload = new Payload()
                .arg("org", orgName)
                .arg("secretName", "secret_" + randomString())
                .archive(resource("cryptoCreate"));

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*result.ok: true.*");
        proc.assertLog(".*result.password: pAss123qweasd.*");
        proc.assertLog(".*credentials-masked: .*password=\\*\\*\\*\\*\\*\\*.*");
        proc.assertLog(".*credentials: .*password=" + "123".replaceAll(".", "$0 ") + ".*");
    }

    @Test
    public void testMasked() throws Exception {
        ApiClient apiClient = concord.apiClient();

        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(apiClient);
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        ProjectsApi projectsApi = new ProjectsApi(apiClient);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.OWNERS));

        // ---

        String mySecretPwd = "pwd_" + randomPwd();

        String myStringSecretName = "secret_" + randomString();
        String myStringSecretValue = "value_" + randomString();
        concord.secrets().createSecret(NewSecretQuery.builder()
                        .org(orgName)
                        .name(myStringSecretName)
                        .storePassword(mySecretPwd)
                        .build(),
                myStringSecretValue.getBytes());

        // ---

        Payload payload = new Payload()
                .org(orgName)
                .project(projectName)
                .archive(resource("crypto-masked"))
                .arg("myOrg", orgName)
                .arg("mySecretPwd", mySecretPwd)
                .arg("myStringSecret", myStringSecretName);

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        proc.assertLog(".*String: \\*\\*\\*\\*\\*\\*.*");

        proc.submitForm("myForm", Collections.singletonMap("name", "test"));

        proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*String after suspend: \\*\\*\\*\\*\\*\\*.*");
    }
}
