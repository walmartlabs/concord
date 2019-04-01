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

public class AnsibleEventProcessorIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        URI uri = ProcessIT.class.getResource("ansibleEventProcessor").toURI();
        byte[] payload = archive(uri, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        AnsibleProcessApi ansibleApi = new AnsibleProcessApi(getApiClient());

        while (true) {
            AnsibleStatsEntry stats = ansibleApi.stats(pir.getInstanceId());
            if (match(stats)) {
                break;
            }
            Thread.sleep(1000);
        }
    }

    private static boolean match(AnsibleStatsEntry e) {
        if (e.getUniqueHosts() != 1) {
            return false;
        }

        List<String> groups = e.getHostGroups();
        if (groups == null || groups.size() != 1) {
            return false;
        }

        if (!groups.get(0).equals("local")) {
            return false;
        }

        Map<String, Integer> stats = e.getStats();

        if (stats.get("OK") != 1) {
            return false;
        }

        if (stats.get("SKIPPED") != 0) {
            return false;
        }

        return true;
    }
}
