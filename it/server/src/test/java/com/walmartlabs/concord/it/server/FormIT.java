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
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.*;

public class FormIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        byte[] payload = archive(FormIT.class.getResource("form").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        ProcessEntry pe = waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);
        assertEquals(StatusEnum.SUSPENDED, pe.getStatus());

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.isCustom());

        String formName = f0.getName();

        Map<String, Object> data = Collections.singletonMap("firstName", firstName);
        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*100223.*", ab);

        // ---

        forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        formName = forms.get(0).getName();

        data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("rememberMe", true);
        data.put("file", "file-content");

        fsr = formsApi.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, psr.getStatus());

        // ---

        ab = getLog(psr.getLogFileName());
        assertLog(".*" + firstName + " " + lastName + ".*", ab);
        assertLog(".*100323.*", ab);
        assertLog(".*r3d.*", ab);
        assertLog(".*FILE_PATH _form_files/myForm2/file.*", ab);
        assertLog(".*FILE file-content.*", ab);
        assertLog(".*AAA true.*", ab);
    }

    @Test
    public void testSubmitMultipart() throws Exception {
        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        byte[] payload = archive(FormIT.class.getResource("form").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.isCustom());

        String formName = f0.getName();

        Map<String, Object> data = Collections.singletonMap("firstName", firstName);
        FormSubmitResponse fsr = request("/api/v1/process/" + spr.getInstanceId() + "/form/" + formName + "/multipart", data, FormSubmitResponse.class);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*100223.*", ab);

        // ---

        forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        formName = forms.get(0).getName();

        data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("rememberMe", true);
        data.put("file", "file-content".getBytes());

        fsr = request("/api/v1/process/" + spr.getInstanceId() + "/form/" + formName + "/multipart", data, FormSubmitResponse.class);
        assertTrue(fsr.isOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, psr.getStatus());

        // ---

        ab = getLog(psr.getLogFileName());
        assertLog(".*" + firstName + " " + lastName + ".*", ab);
        assertLog(".*100323.*", ab);
        assertLog(".*r3d.*", ab);
        assertLog(".*FILE file-content.*", ab);
        assertLog(".*AAA true.*", ab);
    }

    @Test
    public void testValues() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formValues").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formResource = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formResource.list(spr.getInstanceId());

        FormListEntry f0 = forms.get(0);
        String formName = f0.getName();

        Map<String, Object> data = Collections.singletonMap("name", "Concord");
        FormSubmitResponse fsr = formResource.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        // ---

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, psr.getStatus());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test
    public void testAdditionalValuesSubmit() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formValuesSubmit").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        FormListEntry f0 = forms.get(0);
        String formName = f0.getName();

        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formName, Collections.emptyMap());
        assertTrue(fsr.isOk());

        // ---

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, psr.getStatus());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*we got 123 hello 234.*", ab);
    }

    @Test
    public void testExternalFormFile() throws Exception {
        String fieldValue = "value_" + randomString();

        byte[] payload = archive(FormIT.class.getResource("formExternal").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.isCustom());

        String formName = f0.getName();

        Map<String, Object> data = Collections.singletonMap("myField", fieldValue);
        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*We got " + fieldValue + ".*", ab);
    }

    @Test
    public void testMultiValueInput() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formMultiValue").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formResource = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formResource.list(spr.getInstanceId());

        FormListEntry f0 = forms.get(0);
        String formName = f0.getName();

        List<String> skills = new ArrayList<>();
        skills.add("angular");
        skills.add("react");
        Map<String, Object> data = Collections.singletonMap("skills", skills);
        FormSubmitResponse fsr = formResource.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        // ---

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, psr.getStatus());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*(Skills ->) \\[(angular|react), (react|angular)\\].*", ab);
    }

    @Test
    @Timeout(value = 60000, unit= TimeUnit.MILLISECONDS)
    public void testDynamicFields() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("dynamicFormFields").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        String formName = f0.getName();

        String fieldValue = "test_" + randomString();
        Map<String, Object> data = Collections.singletonMap("x", fieldValue);
        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, psr.getStatus());

        // ---

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*We got: " + fieldValue + ".*", ab);
    }

    @Test
    public void testReadonlyField() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formReadonlyField").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry form = forms.get(0);
        String formName = form.getName();

        String fieldValue = "test_" + randomString();
        Map<String, Object> data = Collections.singletonMap("myValue", fieldValue);
        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, psr.getStatus());

        // ---

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*default value.*", ab);
    }

    @Test
    public void testOptionalFileTypeField() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formOptionalFileTypeField").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry form = forms.get(0);
        String formName = form.getName();

        Map<String, Object> data = Collections.emptyMap();
        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());

        assertEquals(StatusEnum.FINISHED, psr.getStatus());
    }

    @Test
    public void testFormCallWithExpression() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formCallWithExpression").toURI());

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.formNameVar", "myForm");
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());

        FormListEntry f0 = forms.get(0);
        String formName = f0.getName();

        Map<String, Object> data = Collections.singletonMap("name", "Concord");
        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        // ---

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, psr.getStatus());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test
    public void testFormLabelInterpolation() throws Exception {
        String xValue = "x_" + randomString();

        // ---

        byte[] payload = archive(FormIT.class.getResource("formLabelExpression").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.x", xValue);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> l = formsApi.list(spr.getInstanceId());
        assertEquals(1, l.size());

        FormInstanceEntry f = formsApi.get(spr.getInstanceId(), l.get(0).getName());
        assertNotNull(f);

        assertEquals(1, f.getFields().size());
        Field field = f.getFields().get(0);
        assertEquals(xValue, field.getLabel());
    }

    @Test
    public void testSingleExpressionAllowedValue() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formSingleAllowedValue").toURI());

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        waitForStatus(processApi, spr.getInstanceId(), StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());

        FormListEntry f0 = forms.get(0);
        String formName = f0.getName();

        Map<String, Object> data = Collections.emptyMap();
        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());

        // ---

        ProcessEntry psr = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(StatusEnum.FINISHED, psr.getStatus());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*field1: one.*", ab);
    }
}
