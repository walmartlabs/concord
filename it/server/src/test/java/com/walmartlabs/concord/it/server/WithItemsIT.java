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

import java.net.URI;
import java.util.*;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.assertEquals;

public class WithItemsIT extends AbstractServerIT {

    @Test(timeout = 90000)
    public void testAnsible() throws Exception {
        URI uri = ProcessIT.class.getResource("ansibleWithItems").toURI();
        byte[] payload = archive(uri, ITConstants.DEPENDENCIES_DIR);

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
        assertLog(".*Hello!.*", ab);
        assertLog(".*Hi there!.*", ab);
        assertLog(".*Howdy!.*", ab);
    }

    @Test(timeout = 90000)
    public void testForms() throws Exception {
        URI uri = ProcessIT.class.getResource("formsWithItems").toURI();
        byte[] payload = archive(uri, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formResource = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formResource.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        formResource.submit(spr.getInstanceId(), forms.get(0).getName(), Collections.emptyMap());

        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);
        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello!.*", ab);

        // ---

        forms = formResource.list(spr.getInstanceId());
        assertEquals(1, forms.size());

        formResource.submit(spr.getInstanceId(), forms.get(0).getName(), Collections.emptyMap());

        pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);
        ab = getLog(pir.getLogFileName());
        assertLog(".*Hi there!.*", ab);
    }

    @Test(timeout = 60000)
    public void testExternalItems() throws Exception {
        URI uri = ProcessIT.class.getResource("externalWithItems").toURI();
        byte[] payload = archive(uri, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("request", Collections.singletonMap("arguments",
                Collections.singletonMap("myItems",
                        Arrays.asList("Hello!", "Hi there!", "Howdy!"))));
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello!.*", ab);
        assertLog(".*Hi there!.*", ab);
        assertLog(".*Howdy!.*", ab);
    }

    @Test(timeout = 60000)
    public void testLotsOfItems() throws Exception {
        URI uri = ProcessIT.class.getResource("externalWithItems").toURI();
        byte[] payload = archive(uri, ITConstants.DEPENDENCIES_DIR);

        // ---

        List<String> items = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            items.add("item_" + randomString());
        }

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("request", Collections.singletonMap("arguments",
                Collections.singletonMap("myItems", items)));
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());
    }

    @Test(timeout = 60000)
    public void testSubsequentCalls() throws Exception {
        URI uri = ProcessIT.class.getResource("multipleWithItems").toURI();
        byte[] payload = archive(uri, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());
    }
}
