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
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AnsibleEventIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    @SuppressWarnings("unchecked")
    public void testEvent() throws Exception {
        URI uri = ProcessIT.class.getResource("ansibleEvent").toURI();
        byte[] payload = archive(uri, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> l = eventsApi.list(pir.getInstanceId(), null, null,-1);
        assertFalse(l.isEmpty());

        long cnt = l.stream().filter(e -> {
            if (!(e.getData() instanceof Map)) {
                return false;
            }

            Map<String, Object> m = (Map<String, Object>) e.getData();

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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    @SuppressWarnings("unchecked")
    public void testIgnoredFailures() throws Exception {
        URI uri = ProcessIT.class.getResource("ansibleIgnoredFailures").toURI();
        byte[] payload = archive(uri, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> l = eventsApi.list(pir.getInstanceId(), null, null, -1);
        assertFalse(l.isEmpty());

        long cnt = l.stream().filter(e -> {
            if (!(e.getData() instanceof Map)) {
                return false;
            }

            Map<String, Object> m = (Map<String, Object>) e.getData();

            Object ignoreErrors = m.get("ignore_errors");
            if (ignoreErrors == null) {
                return false;
            }

            return Boolean.TRUE.equals(ignoreErrors);
        }).count();

        assertEquals(1, cnt);
    }
}
