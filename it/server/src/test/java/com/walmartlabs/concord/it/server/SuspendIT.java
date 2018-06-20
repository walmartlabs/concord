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

import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.Test;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.Assert.assertEquals;

public class SuspendIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        URI dir = SuspendIT.class.getResource("suspend").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForStatus(processApi, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*aaaa.*", ab);

        // ---

        String testValue = "test#" + randomString();
        Map<String, Object> args = Collections.singletonMap("testValue", testValue);
        Map<String, Object> req = Collections.singletonMap(Constants.Request.ARGUMENTS_KEY, args);

        processApi.resume(spr.getInstanceId(), "ev1", req);

        pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        waitForLog(pir.getLogFileName(), ".*bbbb.*");
        waitForLog(pir.getLogFileName(), ".*" + Pattern.quote(testValue) + ".*");
    }
}
