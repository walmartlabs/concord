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
import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import org.junit.Rule;
import org.junit.Test;

import static com.walmartlabs.concord.it.common.ITUtils.randomPwd;
import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.Assert.assertEquals;

public class CryptoIT {

    @Rule
    public final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * Tests various methods of the 'crypto' plugin.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        ApiClient apiClient = concord.apiClient();

        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(apiClient);
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        ProjectsApi projectsApi = new ProjectsApi(apiClient);
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.OWNERS));

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
                .archive(ProcessIT.class.getResource("crypto").toURI())
                .arg("myOrg", orgName)
                .arg("mySecretPwd", mySecretPwd)
                .arg("myStringSecret", myStringSecretName)
                .arg("myKeypair", myKeypairName)
                .arg("myCredentials", myCredentialsName)
                .arg("mySecretFile", mySecretFileName)
                .arg("myRawString", myRawString);

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        proc.assertLog(".*String: " + myStringSecretValue + ".*");
        proc.assertLog(".*Keypair: \\{.*private.*}.*");
        proc.assertLog(".*Credentials: .*" + myPassword + ".*");
        proc.assertLog(".*File: .*");
        proc.assertLog(".*Encrypted string: " + myRawString + ".*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCreate() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(concord.apiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        Payload payload = new Payload()
                .arg("org", orgName)
                .arg("secretName", "secret_" + randomString())
                .archive(CryptoIT.class.getResource("cryptoCreate").toURI());

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        proc.assertLog(".*result.ok: true.*");
        proc.assertLog(".*result.password: pAss123qweasd.*");
        proc.assertLog(".*credentials: .*password=123.*");
    }
}
