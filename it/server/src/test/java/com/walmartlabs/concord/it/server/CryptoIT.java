package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.OrganizationResource;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class CryptoIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testPlain() throws Exception {
        String orgName = "Default";

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();
        String storePassword = "store@" + randomString();

        addPlainSecret(orgName, secretName, false, storePassword, secretValue.getBytes());

        // ---

        test("cryptoPlain", secretName, storePassword, ".*value=" + secretValue + ".*");
    }

    @Test(timeout = 60000)
    public void testUsernamePassword() throws Exception {
        String orgName = "Default";

        // ---

        String secretName = "secret@" + randomString();
        String secretUsername = "username@" + randomString();
        String secretPassword = "password@" + randomString();
        String storePassword = "store@" + randomString();

        addUsernamePassword(orgName, secretName, false, storePassword, secretUsername, secretPassword);

        // ---

        test("cryptoPwd", secretName, storePassword, ".*" + secretUsername + " " + secretPassword + ".*");
    }

    @Test(timeout = 60000)
    public void testExportAsFile() throws Exception {
        String orgName = "Default";

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();
        String storePassword = "store@" + randomString();

        addPlainSecret(orgName, secretName, false, storePassword, secretValue.getBytes());

        // ---

        test("cryptoFile", secretName, storePassword, ".*We got " + secretValue + ".*");
    }

    @Test(timeout = 60000)
    public void testExportAsFileWithOrg() throws Exception {
        String orgName = "org@" + randomString();

        OrganizationResource organizationResource = proxy(OrganizationResource.class);
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();
        String storePassword = "store@" + randomString();

        addPlainSecret(orgName, secretName, false, storePassword, secretValue.getBytes());

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("cryptoFileWithOrg").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "archive", payload,
                "arguments.secretName", secretName,
                "arguments.pwd", storePassword,
                "arguments.secretOrgName", orgName));

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*We got " + secretValue + ".*", ab);
    }

    @Test(timeout = 60000)
    public void testWithoutPassword() throws Exception {
        String orgName = "org@" + randomString();

        OrganizationResource organizationResource = proxy(OrganizationResource.class);
        organizationResource.createOrUpdate(new OrganizationEntry(orgName));

        // ---

        String secretName = "secret@" + randomString();
        String secretValue = "value@" + randomString();

        addPlainSecret(orgName, secretName, false, null, secretValue.getBytes());

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("cryptoWithoutPassword").toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "archive", payload,
                "arguments.secretName", secretName,
                "arguments.secretOrgName", orgName));

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*We got " + secretValue + ".*", ab);
    }

    private void test(String project, String secretName, String storePassword, String log) throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource(project).toURI());

        StartProcessResponse spr = start(ImmutableMap.of(
                "archive", payload,
                "arguments.secretName", secretName,
                "arguments.pwd", storePassword));

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(log, ab);
    }
}
