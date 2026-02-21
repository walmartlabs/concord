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
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.*;

public class AnsibleEventIT extends AbstractServerIT {

    @Test
    @SuppressWarnings("unchecked")
    public void testEvent() throws Exception {
        URI uri = AnsibleEventIT.class.getResource("ansibleEvent").toURI();
        byte[] payload = archive(uri);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> l = eventsApi.listProcessEvents(pir.getInstanceId(), null, null, null, null, null, null, -1);
        assertFalse(l.isEmpty());

        long cnt = l.stream().filter(e -> {
            if (e.getData() == null) {
                return false;
            }

            Map<String, Object> m = e.getData();

            Map<String, Object> r = (Map<String, Object>) m.get("result");
            if (r == null) {
                return false;
            }

            String msg = (String) r.get("msg");
            if (msg == null) {
                return false;
            }

            return msg.equals("Hi there!");
        }).count();

        assertEquals(1, cnt);
    }

    @Test
    public void testIgnoredFailures() throws Exception {
        URI uri = AnsibleEventIT.class.getResource("ansibleIgnoredFailures").toURI();
        byte[] payload = archive(uri);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> l = eventsApi.listProcessEvents(pir.getInstanceId(), null, null, null, null, null, null, -1);
        assertFalse(l.isEmpty());

        long cnt = l.stream().filter(e -> {
            if (e.getData() == null) {
                return false;
            }

            Map<String, Object> m = e.getData();

            Object ignoreErrors = m.get("ignore_errors");
            if (ignoreErrors == null) {
                return false;
            }

            return Boolean.TRUE.equals(ignoreErrors);
        }).count();

        assertEquals(1, cnt);
    }

    /**
     * Runs a playbook that fails on one of the steps.
     * Verifies that failed host events are correctly recorded.
     */
    @Test
    public void testFailedHosts() throws Exception {
        URI uri = AnsibleEventIT.class.getResource("ansibleFailedHosts").toURI();
        byte[] payload = archive(uri);

        // ---

        StartProcessResponse spr = start(payload);

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLogAtLeast(".*'msg' is undefined.*", 1, ab);

        // ---

        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> l = eventsApi.listProcessEvents(pe.getInstanceId(), "ANSIBLE", null, null, null, "post", null, -1);
        assertFalse(l.isEmpty());

        for (ProcessEventEntry e : l) {
            Map<String, Object> m = e.getData();
            if (m.get("task").equals("fail") && m.get("status").equals("FAILED")) {
                return;
            }
        }

        fail("Can't find the required events");
    }
}
