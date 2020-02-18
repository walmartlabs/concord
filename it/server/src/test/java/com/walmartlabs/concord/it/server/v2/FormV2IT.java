package com.walmartlabs.concord.it.server.v2;

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
import com.walmartlabs.concord.it.server.AbstractServerIT;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.*;

public class FormV2IT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        byte[] payload = archive(FormV2IT.class.getResource("form").toURI());

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        ProcessEntry pe = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, pe.getStatus());

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());

        List<FormListEntry> forms = formsApi.list(spr.getInstanceId());
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

        FormSubmitResponse fsr = formsApi.submit(spr.getInstanceId(), formName, data);
        assertTrue(fsr.isOk());
        assertTrue(fsr.getErrors() == null || fsr.getErrors().isEmpty());

        pe = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*firstName=" + firstName + ".*", ab);
        assertLog(".*lastName=" + lastName + ".*", ab);
        assertLog(".*age=" + age + ".*", ab);
    }
}
