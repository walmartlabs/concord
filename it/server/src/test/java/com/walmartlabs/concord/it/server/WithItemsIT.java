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

import java.net.URI;
import java.util.*;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WithItemsIT extends AbstractServerIT {

    @Test
    public void testAnsible() throws Exception {
        URI uri = WithItemsIT.class.getResource("ansibleWithItems").toURI();
        byte[] payload = archive(uri);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello!.*", ab);
        assertLog(".*Hi there!.*", ab);
        assertLog(".*Howdy!.*", ab);
    }

    @Test
    public void testForms() throws Exception {
        URI uri = WithItemsIT.class.getResource("formsWithItems").toURI();
        byte[] payload = archive(uri);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        // ---

        ProcessFormsApi formResource = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formResource.listProcessForms(spr.getInstanceId());
        assertEquals(1, forms.size());

        formResource.submitForm(spr.getInstanceId(), forms.get(0).getName(), Collections.emptyMap());

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);
        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello!.*", ab);

        // ---

        forms = formResource.listProcessForms(spr.getInstanceId());
        assertEquals(1, forms.size());

        formResource.submitForm(spr.getInstanceId(), forms.get(0).getName(), Collections.emptyMap());

        pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);
        ab = getLog(pir.getInstanceId());
        assertLog(".*Hi there!.*", ab);
    }

    @Test
    public void testExternalItems() throws Exception {
        URI uri = WithItemsIT.class.getResource("externalWithItems").toURI();
        byte[] payload = archive(uri);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("request", Collections.singletonMap("arguments",
                Collections.singletonMap("myItems",
                        Arrays.asList("Hello!", "Hi there!", "Howdy!"))));
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello!.*", ab);
        assertLog(".*Hi there!.*", ab);
        assertLog(".*Howdy!.*", ab);
    }

    @Test
    public void testLotsOfItems() throws Exception {
        URI uri = WithItemsIT.class.getResource("externalWithItems").toURI();
        byte[] payload = archive(uri);

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

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());
    }

    @Test
    public void testSubsequentCalls() throws Exception {
        URI uri = WithItemsIT.class.getResource("multipleWithItems").toURI();
        byte[] payload = archive(uri);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());
    }
}
