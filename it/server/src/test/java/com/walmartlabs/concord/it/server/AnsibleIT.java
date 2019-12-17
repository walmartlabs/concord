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
import java.util.regex.Pattern;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AnsibleIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testVaultWithMultiplePasswords() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleVaultMultiplePasswords").toURI();
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testVaultWithMultiplePasswordFiles() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleVaultMultiplePasswordFiles").toURI();
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testBadStrings() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleBadStrings").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*THIS TASK CONTAINS SENSITIVE INFORMATION.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testRawStrings() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleRawStrings").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*" + Pattern.quote("message '{{.lalala}}'") + ".*", ab);
        assertLog(".*" + Pattern.quote("message {{.unsafe}}") + ".*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    @SuppressWarnings("unchecked")
    public void testTemplateArgs() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleTemplateArgs").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*message iddqd.*", ab);

        // ---
        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> events = eventsApi.list(pir.getInstanceId(), "ANSIBLE", null, null, null,null, null, null);
        assertNotNull(events);
        // one pre and one post event
        assertEquals(2, events.size());
        Map<String, Object> data = events.stream()
                .filter(e -> "post".equals(e.getData().get("phase")))
                .findAny()
                .orElseThrow(() -> new RuntimeException("post event not found"))
                .getData();
        assertEquals("message iddqd", ((Map<String, Object>) data.get("result")).get("msg"));
    }

    @Test
    public void testExtraVarsFiles() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleExtraVarsFiles").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello from a JSON file!.*", ab);
        assertLog(".*Hello from a YAML file!.*", ab);
    }

    @Test
    public void testMultiInventories() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleMultiInventory").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello aaa.*", ab);
        assertLog(".*Hello bbb.*", ab);
    }

    @Test
    public void testMultiInventoryFiles() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleMultiInventoryFile").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello aaa.*", ab);
        assertLog(".*Hello bbb.*", ab);
    }

    @Test
    public void testLimitWithMultipleHost() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleLimitWithMultipleHost").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLogAtLeast(".*Hello aaa.*", 2, ab);
        assertLogAtLeast(".*Hello ccc.*", 2, ab);
        assertNoLog(".*Hello bbb.*", ab);
    }
}
