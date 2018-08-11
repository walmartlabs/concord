package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.google.common.io.Files;
import com.walmartlabs.concord.client.*;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.assertEquals;

public class AnsibleIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testHello() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansible").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*\"msg\":.*Hello, world.*", ab);
    }

    @Test(timeout = 60000)
    public void testSkipTags() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleSkipTags").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*\"msg\":.*Hello2, world.*", ab);
        assertEquals("unexpected 'Hello, world' log", 0, grep(".*Hello, world.*", ab).size());
    }

    @Test(timeout = 60000)
    public void testVault() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleVault").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test(timeout = 60000)
    public void testTwoAnsibleRuns() throws Exception {
        URI dir = AnsibleIT.class.getResource("twoAnsible").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*\"msg\":.*Hello!.*", ab);
        assertLog(".*\"msg\":.*Bye-bye!.*", ab);
    }

    @Test(timeout = 60000)
    public void testWithForm() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleWithForm").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formsApi.list(pir.getInstanceId());
        assertEquals(1, forms.size());

        formsApi.submit(pir.getInstanceId(), forms.get(0).getName(), Collections.singletonMap("msg", "Hello!"));

        // ---

        pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*\"msg\":.*Hello!.*", ab);
    }

    @Test(timeout = 60000)
    public void testWithFormSuspensionPostAnsible() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleWithPostFormSuspension/payload").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // --

        URI playbookUri = AnsibleIT.class.getResource("ansibleWithPostFormSuspension/playbook/hello.yml").toURI();
        File playbookFileContent = new File(playbookUri);
        InputStream playbookStream = Files.asByteSource(playbookFileContent).openStream();

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("playbook.yml", playbookStream);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formsApi.list(pir.getInstanceId());
        assertEquals(1, forms.size());

        formsApi.submit(pir.getInstanceId(), forms.get(0).getName(), Collections.singletonMap("msg", "Hello!"));

        // ---

        pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello!.*", ab);
    }

    @Test(timeout = 60000)
    public void testExtenalPlaybook() throws Exception {

        URI dir = AnsibleIT.class.getResource("ansibleExternalPlaybook/payload").toURI();
        URL playbookUrl = AnsibleIT.class.getResource("ansibleExternalPlaybook/playbook/hello.yml");
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        File playbook = Paths.get(playbookUrl.toURI()).toFile();
        input.put("myplaybook.yml", new FileInputStream(playbook));
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());


        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());
    }

    @Test(timeout = 60000)
    public void testMergeDefaults() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleMergeDefaults").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());


        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*_callbacks:myCallbackDir.*", ab);
    }

    @Test(timeout = 60000)
    public void testGroupVars() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String secretName = "secret_" + randomString();
        addPlainSecret(orgName, secretName, false, null, "greetings: \"Hi there!\"".getBytes());

        // ---

        URI dir = AnsibleIT.class.getResource("ansibleGroupVars").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.myOrgName", orgName);
        input.put("arguments.mySecretName", secretName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*\"msg\":.*Hi there!.*", ab);
    }

    @Test(timeout = 60000)
    public void testOutVars() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleOutVars").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*\"msg\":.*First hello from ansible.*", ab);
        assertLog(".*\"msg\":.*Second message.*", ab);
    }

    @Test(timeout = 60000)
    public void testStats() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleStats").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*OK:.*127.0.0.1.*", ab);
    }
}
