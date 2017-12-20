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

import com.walmartlabs.concord.server.api.process.*;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

public class FormIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        byte[] payload = archive(FormIT.class.getResource("form").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        // ---

        FormResource formResource = proxy(FormResource.class);

        List<FormListEntry> forms = formResource.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.isCustom());

        String formId = f0.getFormInstanceId();

        Map<String, Object> data = Collections.singletonMap("firstName", firstName);
        FormSubmitResponse fsr = formResource.submit(spr.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*100223.*", ab);

        // ---

        forms = formResource.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        formId = forms.get(0).getFormInstanceId();

        data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("rememberMe", true);
        data.put("file", "file-content");

        fsr = formResource.submit(spr.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        psr = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, psr.getStatus());

        // ---

        ab = getLog(psr.getLogFileName());
        assertLog(".*" + firstName + " " + lastName + ".*", ab);
        assertLog(".*100323.*", ab);
        assertLog(".*red.*", ab);
        assertLog(".*FILE file-content.*", ab);
        assertLog(".*AAA true.*", ab);
    }

    @Test(timeout = 30000)
    public void testSubmitMultipart() throws Exception {
        String firstName = "john_" + randomString();
        String lastName = "smith_" + randomString();
        byte[] payload = archive(FormIT.class.getResource("form").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        // ---

        FormResource formResource = proxy(FormResource.class);

        List<FormListEntry> forms = formResource.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        FormListEntry f0 = forms.get(0);
        assertFalse(f0.isCustom());

        String formId = f0.getFormInstanceId();

        Map<String, Object> data = Collections.singletonMap("firstName", firstName);
        FormSubmitResponse fsr = request("api/v1/process/" + spr.getInstanceId() + "/form/" + formId, data, FormSubmitResponse.class);
        assertTrue(fsr.isOk());

        ProcessEntry psr = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*100223.*", ab);

        // ---

        forms = formResource.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        // ---

        formId = forms.get(0).getFormInstanceId();

        data = new HashMap<>();
        data.put("lastName", lastName);
        data.put("rememberMe", true);
        data.put("file", "file-content".getBytes());

        fsr = request("api/v1/process/" + spr.getInstanceId() + "/form/" + formId, data, FormSubmitResponse.class);
        assertTrue(fsr.isOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        psr = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, psr.getStatus());

        // ---

        ab = getLog(psr.getLogFileName());
        assertLog(".*" + firstName + " " + lastName + ".*", ab);
        assertLog(".*100323.*", ab);
        assertLog(".*red.*", ab);
        assertLog(".*FILE file-content.*", ab);
        assertLog(".*AAA true.*", ab);
    }

    @Test(timeout = 30000)
    public void testValues() throws Exception {
        byte[] payload = archive(FormIT.class.getResource("formValues").toURI());

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        // ---

        FormResource formResource = proxy(FormResource.class);

        List<FormListEntry> forms = formResource.list(spr.getInstanceId());

        FormListEntry f0 = forms.get(0);
        String formId = f0.getFormInstanceId();

        Map<String, Object> data = Collections.singletonMap("name", "Concord");
        FormSubmitResponse fsr = formResource.submit(spr.getInstanceId(), formId, data);
        assertTrue(fsr.isOk());

        // ---

        ProcessEntry psr = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, psr.getStatus());

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*Hello, Concord.*", ab);
    }
}
