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
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.ConfigurationUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AnsibleIT extends AbstractServerIT {

    @Test
    public void testHello() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansible").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*\"msg\":.*Hello, world.*", ab);
    }

    @Test
    public void testSkipTags() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleSkipTags").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*\"msg\":.*Hello2, world.*", ab);
        assertEquals(0, grep(".*Hello, world.*", ab).size(), "unexpected 'Hello, world' log");
    }

    @Test
    public void testVault() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleVault").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test
    public void testVaultWithMultiplePasswords() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleVaultMultiplePasswords").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test
    public void testVaultWithMultiplePasswordFiles() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleVaultMultiplePasswordFiles").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTwoAnsibleRuns() throws Exception {
        URI dir = AnsibleIT.class.getResource("twoAnsible").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*\"msg\":.*Hello!.*", ab);
        assertLog(".*\"msg\":.*Bye-bye!.*", ab);

        // ---

        try (InputStream resp = processApi.downloadAttachment(spr.getInstanceId(), "ansible_stats_v2.json")) {
            assertNotNull(resp);

            List<Map<String, Object>> stats = fromJson(resp, List.class);

            assertEquals(2, stats.size());

            assertEquals("playbook/hello.yml", stats.get(0).get("playbook"));
            Collection<String> oks = (Collection<String>)ConfigurationUtils.get(stats.get(0), "stats", "ok");
            assertNotNull(oks);
            assertEquals(1, oks.size());
            assertEquals("127.0.0.1", oks.iterator().next());
        }
    }

    @Test
    public void testWithForm() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleWithForm").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formsApi.listProcessForms(pir.getInstanceId());
        assertEquals(1, forms.size());

        formsApi.submitForm(pir.getInstanceId(), forms.get(0).getName(), Collections.singletonMap("msg", "Hello!"));

        // ---

        pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*\"msg\":.*Hello!.*", ab);
    }

    @Test
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

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formsApi.listProcessForms(pir.getInstanceId());
        assertEquals(1, forms.size());

        formsApi.submitForm(pir.getInstanceId(), forms.get(0).getName(), Collections.singletonMap("msg", "Hello!"));

        // ---

        pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello!.*", ab);
    }

    @Test
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

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());
    }

    @Test
    public void testMergeDefaults() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleMergeDefaults").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*_callbacks:myCallbackDir.*", ab);
    }

    @Test
    public void testGroupVars() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

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

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*\"msg\":.*Hi there!.*", ab);
    }

    @Test
    public void testOutVars() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleOutVars").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*\"msg\":.*First hello from ansible.*", ab);
        assertLog(".*\"msg\":.*Second message.*", ab);
    }

    @Test
    public void testStats() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleStats").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*OK:.*127.0.0.1.*", ab);
    }

    @Test
    public void testBadStrings() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleBadStrings").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*THIS TASK CONTAINS SENSITIVE INFORMATION.*", ab);
    }

    @Test
    public void testRawStrings() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleRawStrings").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*" + Pattern.quote("message '{{.lalala}}'") + ".*", ab);
        assertLog(".*" + Pattern.quote("message {{.unsafe}}") + ".*", ab);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTemplateArgs() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleTemplateArgs").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*message iddqd.*", ab);

        // ---
        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> events = eventsApi.listProcessEvents(pir.getInstanceId(), "ANSIBLE", null, null, null, null, null, null);
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

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello from a JSON file!.*", ab);
        assertLog(".*Hello from a YAML file!.*", ab);
    }

    @Test
    public void testMultiInventories() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleMultiInventory").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello aaa.*", ab);
        assertLog(".*Hello bbb.*", ab);
    }

    @Test
    public void testMultiInventoryFiles() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleMultiInventoryFile").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello aaa.*", ab);
        assertLog(".*Hello bbb.*", ab);
    }

    @Test
    public void testLimitWithMultipleHost() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleLimitWithMultipleHost").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLogAtLeast(".*Hello aaa.*", 2, ab);
        assertLogAtLeast(".*Hello ccc.*", 2, ab);
        assertNoLog(".*Hello bbb.*", ab);
    }

    @Test
    public void testInventoryMixMatch() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleInventoryMix").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello!.*", ab);
    }

    @Test
    public void testInventoryNameInvalidChars() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleInventoryName").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello!.*", ab);
    }

    @Test
    public void testLogFiltering() throws Exception {
        // run w/o filtering first

        URI dir = AnsibleIT.class.getResource("ansibleLogFiltering").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("arguments.doFilter", false);
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello! my_password=.*", ab);

        // and then run with the filtering enabled

        input = new HashMap<>();
        input.put("arguments.doFilter", true);
        input.put("archive", payload);

        spr = start(input);

        // ---

        pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        ab = getLog(pir.getInstanceId());
        assertNoLog(".*Hello! my_password=.*", ab);
        assertLog(".*SENSITIVE INFORMATION.*", ab);
    }
}
