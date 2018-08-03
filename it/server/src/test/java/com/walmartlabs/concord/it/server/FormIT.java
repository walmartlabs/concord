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



import com.walmartlabs.concord.client.*;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

public class FormIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        byte[] payload = archive(FormIT.class.getResource("form").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.isCustom());

        String formId = f0.getFormInstanceId();

        Map<String, Object> data = Collections.singletonMap("firstName", firstName);
        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*100223.*", ab);

        // ---

        forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        formId = forms.get(0).getFormInstanceId();

        data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("rememberMe", true);
        data.put("file", "file-content");

        fsr = formsApi.submit(spr.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, psr.getStatus());

        // ---

        ab = getLog(psr.getLogFileName());
        assertLog(".*" + firstName + " " + lastName + ".*", ab);
        assertLog(".*100323.*", ab);
        assertLog(".*r3d.*", ab);
        assertLog(".*FILE_PATH _form_files/myForm2/file.*", ab);
        assertLog(".*FILE file-content.*", ab);
        assertLog(".*AAA true.*", ab);
    }

    @Test(timeout = 60000)
    public void testSubmitMultipart() throws Exception {
        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        byte[] payload = archive(FormIT.class.getResource("form").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.isCustom());

        String formId = f0.getFormInstanceId();

        Map<String, Object> data = Collections.singletonMap("firstName", firstName);
        FormSubmitResponse fsr = request("/api/v1/process/" + spr.getInstanceId() + "/form/" + formId, data, FormSubmitResponse.class);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*100223.*", ab);

        // ---

        forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        formId = forms.get(0).getFormInstanceId();

        data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("rememberMe", true);
        data.put("file", "file-content".getBytes());

        fsr = request("/api/v1/process/" + spr.getInstanceId() + "/form/" + formId, data, FormSubmitResponse.class);
        assertTrue(fsr.isOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, psr.getStatus());

        // ---

        ab = getLog(psr.getLogFileName());
        assertLog(".*" + firstName + " " + lastName + ".*", ab);
        assertLog(".*100323.*", ab);
        assertLog(".*r3d.*", ab);
        assertLog(".*FILE file-content.*", ab);
        assertLog(".*AAA true.*", ab);
    }

    @Test(timeout = 60000)
    public void testValues() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formValues").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formResource = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formResource.list(spr.getInstanceId());

        FormListEntry f0 = forms.get(0);
        String formId = f0.getFormInstanceId();

        Map<String, Object> data = Collections.singletonMap("name", "Concord");
        FormSubmitResponse fsr = formResource.submit(spr.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());

        // ---

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, psr.getStatus());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test(timeout = 60000)
    public void testAdditionalValuesSubmit() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formValuesSubmit").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        FormListEntry f0 = forms.get(0);
        String formId = f0.getFormInstanceId();

        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formId, Collections.emptyMap());
        assertTrue(fsr.isOk());

        // ---

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, psr.getStatus());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*we got 123 hello 234.*", ab);
    }

    @Test(timeout = 60000)
    public void testExternalFormFile() throws Exception {
        String fieldValue = "value_" + randomString();

        byte[] payload = archive(FormIT.class.getResource("formExternal").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.isCustom());

        String formId = f0.getFormInstanceId();

        Map<String, Object> data = Collections.singletonMap("myField", fieldValue);
        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*We got " + fieldValue + ".*", ab);
    }

    @Test(timeout = 60000)
    public void testMultiValueInput() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formMultiValue").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formResource = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formResource.list(spr.getInstanceId());

        FormListEntry f0 = forms.get(0);
        String formId = f0.getFormInstanceId();

        List<String> skills = new ArrayList<>();
        skills.add("angular");
        skills.add("react");
        Map<String, Object> data = Collections.singletonMap("skills", skills);
        FormSubmitResponse fsr = formResource.submit(spr.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());

        // ---

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, psr.getStatus());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*(Skills ->) \\[(angular|react), (react|angular)\\].*", ab);
    }
}
