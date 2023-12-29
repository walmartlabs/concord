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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RepositoryRefreshIT extends AbstractServerIT {
    /**
     * Test case is ignored as repository refresh task is enabled only for concord runtime-v2
     * @throws Exception
     */
    @Disabled
    @Test
    public void test() throws Exception {
        String orgName = "ConcordSystem";
        String projectName = "concordTriggers";
        String repoName = "triggers";

        // ---

        byte[] payload = archive(RepositoryRefreshIT.class.getResource("repositoryRefresh").toURI());

        Map<String, Object> req = new HashMap<>();
        req.put("archive", payload);

        Map<String, Object> args = new HashMap<>();
        args.put("orgName", orgName);
        args.put("projectName", projectName);
        args.put("repoName", repoName);

        req.put("request", Collections.singletonMap("arguments", args));

        StartProcessResponse spr = start(req);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        TriggersApi triggerResource = new TriggersApi(getApiClient());
        List<TriggerEntry> list = triggerResource.listTriggers(orgName, projectName, repoName);
        assertFalse(list.isEmpty());
    }
}
