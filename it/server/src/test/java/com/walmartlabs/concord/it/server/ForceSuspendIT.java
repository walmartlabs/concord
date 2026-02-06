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

import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import com.walmartlabs.concord.it.common.ITConstants;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;

public class ForceSuspendIT extends AbstractServerIT {

    @Test
    public void testTask() throws Exception {
        String eventName = "ev_" + randomString();

        byte[] payload = archive(ForceSuspendIT.class.getResource("suspendTask").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.eventName", eventName);

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("dependencies", new String[]{"mvn://com.walmartlabs.concord.it.tasks:suspend-test:" + ITConstants.PROJECT_VERSION});
        input.put("request", cfg);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);
        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Requesting suspend.*", ab);
        assertLog(".*Whoa!.*", 0, ab);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        processApi.resume(pir.getInstanceId(), eventName, null, null);
        pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        ab = getLog(pir.getInstanceId());

        assertLog(".*Whoa!.*", ab);
    }
}
