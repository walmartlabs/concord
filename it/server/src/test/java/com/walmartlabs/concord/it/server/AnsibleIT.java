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
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.Assert.assertEquals;

public class AnsibleIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testHello() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansible").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*\"msg\":.*Hello, world.*", ab);
    }

    @Test(timeout = 30000)
    public void testSkipTags() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleSkipTags").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*\"msg\":.*Hello2, world.*", ab);
        assertEquals("unexpected 'Hello, world' log", 0, grep(".*Hello, world.*", ab).size());
    }

    @Test(timeout = 30000)
    public void testVault() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleVault").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        // ---

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test(timeout = 30000)
    public void testTwoAnsibleRuns() throws Exception {
        URI dir = AnsibleIT.class.getResource("twoAnsible").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*\"msg\":.*Hello!.*", ab);
        assertLog(".*\"msg\":.*Bye-bye!.*", ab);
    }

    @Test(timeout = 30000)
    public void testWithForm() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleWithForm").toURI();
        byte[] payload = archive(dir);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForStatus(processResource, spr.getInstanceId(), ProcessStatus.SUSPENDED);

        // ---

        FormResource formResource = proxy(FormResource.class);
        List<FormListEntry> forms = formResource.list(pir.getInstanceId());
        assertEquals(1, forms.size());

        formResource.submit(pir.getInstanceId(), forms.get(0).getFormInstanceId(), Collections.singletonMap("msg", "Hello!"));

        // ---

        pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*\"msg\":.*Hello!.*", ab);
    }
}
