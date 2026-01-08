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


import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SerializationIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        byte[] payload = archive(SerializationIT.class.getResource("serialization").toURI());

        // ---

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // ---

        ProcessEntry pe = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);
        assertEquals(ProcessEntry.StatusEnum.SUSPENDED, pe.getStatus());

        // ---

        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formsApi.listProcessForms(spr.getInstanceId());
        assertEquals(1, forms.size());

        FormListEntry f = forms.get(0);
        formsApi.submitForm(spr.getInstanceId(), f.getName(),
                Collections.singletonMap("y", "hello"));

        // ---

        pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        byte[] ab = getLog(pe.getInstanceId());

        assertLog(".*hello.*", ab);
    }

    @Test
    public void testNonSerializable() throws Exception {
        byte[] payload = archive(SerializationIT.class.getResource("nonSerializableTest").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("request", Collections.singletonMap("dependencies",
                new String[]{"mvn://com.walmartlabs.concord.it.tasks:serialization-test:" + ITConstants.PROJECT_VERSION}));

        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);
        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Not serializable value: test.*", ab);
    }
}
