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
import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import com.google.common.io.CharStreams;
import com.walmartlabs.concord.client.FormListEntry;
import com.walmartlabs.concord.client.FormSubmitResponse;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.sdk.MapUtils;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.junit.Rule;
import org.junit.Test;
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
import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.Assert.*;

public class FormIT {

    @Rule
    public final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * A straightforward single form process.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        Payload payload = new Payload()
                .archive(FormIT.class.getResource("form").toURI());

        // ---

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, pe.getStatus());

        // ---

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        // ---

        FormListEntry myForm = forms.get(0);
        assertFalse(myForm.isCustom());

        String formName = myForm.getName();

        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        int age = ThreadLocalRandom.current().nextInt(100);

        Map<String, Object> data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("firstName", firstName);
        data.put("age", age);

        FormSubmitResponse fsr = proc.submitForm(formName, data);
        assertTrue(fsr.isOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

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
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testFormOnCancel() throws Exception {
        Payload payload = new Payload()
                .archive(FormIT.class.getResource("formOnCancel").toURI());

        // ---

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, pe.getStatus());

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        FormListEntry myForm = forms.get(0);

        String firstName = "john_" + randomString();
        int age = ThreadLocalRandom.current().nextInt(100);

        Map<String, Object> data = new HashMap<>();
        data.put("firstName", firstName);
        data.put("age", age);

        FormSubmitResponse fsr = proc.submitForm(myForm.getName(), data);
        assertTrue(fsr.isOk());

        // ---

        proc.waitForStatus(ProcessEntry.StatusEnum.RUNNING);
        proc.kill();
        proc.waitForStatus(ProcessEntry.StatusEnum.CANCELLED);

        ConcordProcess child = concord.processes().get(proc.waitForChildStatus(ProcessEntry.StatusEnum.FINISHED).getInstanceId());
        child.assertLog(".*myForm.firstName: " + firstName + ".*");
        child.assertLog(".*myForm.age: " + age + ".*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testFormValues() throws Exception {
        Payload payload = new Payload()
                .archive(FormIT.class.getResource("customFormValues").toURI());

        // ---

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, pe.getStatus());

        // ---

        List<FormListEntry> forms = proc.forms();
        assertEquals(1, forms.size());

        // ---
        String formName = forms.get(0).getName();
        assertEquals("myForm", formName);

        // start session
        startCustomFormSession(concord, pe.getInstanceId(), formName);

        // get data.js
        Map<String, Object> dataJs = getDataJs(concord, pe.getInstanceId(), formName);
        Map<String, Object> values = MapUtils.get(dataJs,"values", Collections.emptyMap());

        assertEquals(4, values.size());
        assertEquals("Moo", values.get("firstName"));
        assertEquals("Xaa", values.get("lastName"));
        assertEquals(3, values.get("sum"));
        assertEquals(ImmutableMap.of("city", "Toronto", "province", "Ontario"), values.get("address"));
    }

    private static void startCustomFormSession(ConcordRule concord, UUID instanceId, String formName) throws Exception {
        URL url = new URL(concord.apiUrlPrefix() + "/api/service/custom_form/" + instanceId + "/" + formName + "/start");
        HttpURLConnection http = (HttpURLConnection)url.openConnection();
        http.setRequestProperty("Authorization", concord.environment().apiToken());
        http.setRequestMethod("POST");
        http.setDoOutput(true);
        http.connect();

        assertEquals(200, http.getResponseCode());

        http.disconnect();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getDataJs(ConcordRule concord, UUID instanceId, String formName) throws Exception {
        URL url = new URL(concord.apiUrlPrefix() + "/forms/" + instanceId + "/" + formName + "/form/data.js");
        HttpURLConnection http = (HttpURLConnection)url.openConnection();
        http.setRequestProperty("Authorization", concord.environment().apiToken());
        http.connect();

        assertEquals(200, http.getResponseCode());

        try (InputStream is = http.getInputStream()) {
            String str = CharStreams.toString(new InputStreamReader(is, StandardCharsets.UTF_8));
            ScriptEngine se = new ScriptEngineManager().getEngineByName("js");
            Object result = se.eval(str);
            assertTrue(result instanceof Map);
            return (Map<String, Object>) toJavaObject(result);
        } finally {
            http.disconnect();
        }
    }

    private static Object toJavaObject(Object scriptObj) {
        if (scriptObj instanceof ScriptObjectMirror) {
            ScriptObjectMirror scriptObjectMirror = (ScriptObjectMirror) scriptObj;
            if (scriptObjectMirror.isArray()) {
                List<Object> list = new ArrayList<>();
                for (Map.Entry<String, Object> entry : scriptObjectMirror.entrySet()) {
                    list.add(toJavaObject(entry.getValue()));
                }
                return list;
            } else {
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, Object> entry : scriptObjectMirror.entrySet()) {
                    map.put(entry.getKey(), toJavaObject(entry.getValue()));
                }
                return map;
            }
        } else {
            return scriptObj;
        }
    }
}
