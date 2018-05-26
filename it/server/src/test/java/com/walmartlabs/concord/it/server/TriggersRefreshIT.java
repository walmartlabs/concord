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

import com.walmartlabs.concord.server.api.org.trigger.TriggerEntry;
import com.walmartlabs.concord.server.api.org.trigger.TriggerResource;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TriggersRefreshIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        String orgName = "ConcordSystem";
        String projectName = "concordTriggers";
        String repoName = "triggers";

        // ---

        byte[] payload = archive(TriggersRefreshIT.class.getResource("triggersRefresh").toURI());

        Map<String, Object> req = new HashMap<>();
        req.put("archive", payload);

        Map<String, Object> args = new HashMap<>();
        args.put("orgName", orgName);
        args.put("projectName", projectName);
        args.put("repoName", repoName);

        req.put("request", Collections.singletonMap("arguments", args));

        StartProcessResponse spr = start(req);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        // ---

        TriggerResource triggerResource = proxy(TriggerResource.class);
        List<TriggerEntry> list = triggerResource.list(orgName, projectName, repoName);
        assertFalse(list.isEmpty());
    }
}
