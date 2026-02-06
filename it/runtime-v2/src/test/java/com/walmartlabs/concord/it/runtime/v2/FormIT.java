package com.walmartlabs.concord.it.runtime.v2;

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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.google.common.io.CharStreams;
import com.walmartlabs.concord.client2.FormListEntry;
import com.walmartlabs.concord.client2.FormSubmitResponse;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.sdk.MapUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static org.junit.jupiter.api.Assertions.*;

public class FormIT extends AbstractTest {

    static ConcordRule concord;

    @BeforeAll
    static void setUp(ConcordRule rule) {
        concord = rule;
    }

    /**
     * A straightforward single form process.
     */
    @Test
    public void test() throws Exception {
        Payload payload = new Payload()
                .archive(resource("form"));

        // ---

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        // ---

        FormListEntry myForm = forms.get(0);
        assertFalse(myForm.getCustom());

        String formName = myForm.getName();

        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        int age = ThreadLocalRandom.current().nextInt(100);

        Map<String, Object> data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("firstName", firstName);
        data.put("age", age);

        FormSubmitResponse fsr = proc.submitForm(formName, data);
        assertTrue(fsr.getOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        assertEquals(0, proc.forms().size());

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*firstName=" + firstName + ".*");
        proc.assertLog(".*lastName=" + lastName + ".*");
        proc.assertLog(".*age=" + age + ".*");
    }

    /**
     * Start a process with a form and a sleep task. Cancel the process while sleeping
     * and check the onCancel process for variables. We expect the submitted values
     * to be available in the onCancel flow.
     */
    @Test
    public void testFormOnCancel() throws Exception {
        Payload payload = new Payload()
                .archive(resource("formOnCancel"));

        // ---

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        FormListEntry myForm = forms.get(0);

        String firstName = "john_" + randomString();
        int age = ThreadLocalRandom.current().nextInt(100);

        Map<String, Object> data = new HashMap<>();
        data.put("firstName", firstName);
        data.put("age", age);

        FormSubmitResponse fsr = proc.submitForm(myForm.getName(), data);
        assertTrue(fsr.getOk());

        // ---

        expectStatus(proc, ProcessEntry.StatusEnum.RUNNING);
        proc.kill();
        expectStatus(proc, ProcessEntry.StatusEnum.CANCELLED);

        ConcordProcess child = concord.processes().get(proc.waitForChildStatus(ProcessEntry.StatusEnum.FINISHED).getInstanceId());
        child.assertLog(".*myForm.firstName: " + firstName + ".*");
        child.assertLog(".*myForm.age: " + age + ".*");
    }

    @Test
    public void testFormValues() throws Exception {
        Payload payload = new Payload()
                .archive(resource("customFormValues"));

        // ---

        assertFormValues(payload);
    }

    @Test
    public void testFormExpressionValues() throws Exception {
        Payload payload = new Payload()
                .entryPoint("callExpressions")
                .archive(resource("customFormValues"));

        // ---

        assertFormValues(payload);
    }

    private void assertFormValues(Payload payload) throws Exception {
        ConcordProcess proc = concord.processes().start(payload);
        ProcessEntry pe = expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        // ---
        FormListEntry form = forms.get(0);
        String formName = form.getName();
        assertEquals("myForm", formName);

        // runAs username from expression
        Map<String, Object> runAs = form.getRunAs();
        assertNotNull(runAs);
        assertEquals("admin", runAs.get("username"));

        // start session
        startCustomFormSession(concord, pe.getInstanceId(), formName);

        // get data.js
        Map<String, Object> dataJs = getDataJs(concord, pe.getInstanceId(), formName);
        Map<String, Object> values = MapUtils.get(dataJs, "values", Collections.emptyMap());

        assertEquals(4, values.size());
        assertEquals("Moo", values.get("firstName"));
        assertEquals("Xaa", values.get("lastName"));
        assertEquals(3, values.get("sum"));
        assertEquals(ImmutableMap.of("city", "Toronto", "province", "Ontario"), values.get("address"));
    }

    @Test
    public void testSubmitInInvalidProcessState() throws Exception {
        Payload payload = new Payload()
                .archive(resource("form"));

        // ---

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        // change process status to emulate resuming from another form
        ProcessApi api = new ProcessApi(concord.apiClient());
        api.updateStatus(proc.instanceId(), UUID.randomUUID().toString(), ProcessEntry.StatusEnum.RESUMING.getValue());

        // form data
        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        int age = ThreadLocalRandom.current().nextInt(100);

        Map<String, Object> data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("firstName", firstName);
        data.put("age", age);

        try {
            proc.submitForm(forms.get(0).getName(), data);
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // change process status back to emulate another form submitted
        api.updateStatus(proc.instanceId(), UUID.randomUUID().toString(), ProcessEntry.StatusEnum.SUSPENDED.getValue());

        forms = proc.forms();
        assertEquals(1, forms.size());

        FormListEntry myForm = forms.get(0);
        assertFalse(myForm.getCustom());

        String formName = myForm.getName();
        assertEquals("myForm", formName);

        FormSubmitResponse fsr = proc.submitForm(formName, data);
        assertTrue(fsr.getOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*firstName=" + firstName + ".*");
        proc.assertLog(".*lastName=" + lastName + ".*");
        proc.assertLog(".*age=" + age + ".*");
    }

    private static void startCustomFormSession(ConcordRule concord, UUID instanceId, String formName) throws Exception {
        URL url = new URL(concord.apiBaseUrl() + "/api/service/custom_form/" + instanceId + "/" + formName + "/start");
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setRequestProperty("Authorization", concord.environment().apiToken());
        http.setRequestMethod("POST");
        http.setDoOutput(true);
        http.connect();

        assertEquals(200, http.getResponseCode());

        http.disconnect();
    }

    @SuppressWarnings({"unchecked", "UnstableApiUsage"})
    private static Map<String, Object> getDataJs(ConcordRule concord, UUID instanceId, String formName) throws Exception {
        URL url = new URL(concord.apiBaseUrl() + "/forms/" + instanceId + "/" + formName + "/form/data.js");
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setRequestProperty("Authorization", concord.environment().apiToken());
        http.connect();

        assertEquals(200, http.getResponseCode());

        try (InputStream is = http.getInputStream()) {
            String str = CharStreams.toString(new InputStreamReader(is, StandardCharsets.UTF_8));
            ScriptEngine se = new ScriptEngineManager().getEngineByName("js");
            Object result = se.eval(str);
            assertTrue(result instanceof Map);
            return (Map<String, Object>) result;
        } finally {
            http.disconnect();
        }
    }
}
