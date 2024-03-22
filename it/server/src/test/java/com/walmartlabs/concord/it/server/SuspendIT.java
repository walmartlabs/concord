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
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SuspendIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        URI dir = SuspendIT.class.getResource("suspend").toURI();
        byte[] payload = archive(dir);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*aaaa.*", ab);

        // ---

        String testValue = "test#" + randomString();
        Map<String, Object> args = Collections.singletonMap("testValue", testValue);
        Map<String, Object> req = Collections.singletonMap(Constants.Request.ARGUMENTS_KEY, args);

        processApi.resume(spr.getInstanceId(), "ev1", null, req);

        pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        waitForLog(pir.getInstanceId(), ".*bbbb.*");
        waitForLog(pir.getInstanceId(), ".*" + Pattern.quote(testValue) + ".*");
    }

    @Test
    public void testSuspendForCompletion() throws Exception {

        // ---
        byte[] payload = archive(SuspendIT.class.getResource("suspendForCompletion").toURI());

        StartProcessResponse parentSpr = start(payload);

        // ---

        ProcessEntry p = waitForStatus(getApiClient(), parentSpr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);
        Set<UUID> childrenIds = p.getChildrenIds();
        assertEquals(2, childrenIds.size());

        for(UUID childId : childrenIds) {
            waitForCompletion(getApiClient(), childId);
        }

        ProcessEntry pir = waitForCompletion(getApiClient(), parentSpr.getInstanceId());
        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*process is resumed.*", ab);
    }

    @Test
    public void testSuspendForForkedProcess() throws Exception {

        // ---
        byte[] payload = archive(SuspendIT.class.getResource("suspendForForkedProcesses").toURI());

        StartProcessResponse parentSpr = start(payload);

        // ---

        ProcessEntry p = waitForStatus(getApiClient(), parentSpr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);
        Set<UUID> childrenIds = p.getChildrenIds();
        assertEquals(3, childrenIds.size());

        for(UUID childId : childrenIds) {
            waitForCompletion(getApiClient(), childId);
        }

        ProcessEntry pir = waitForCompletion(getApiClient(), parentSpr.getInstanceId());
        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*task completed.*", ab);
    }
}
