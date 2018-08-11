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

import com.googlecode.junittoolbox.ParallelRunner;
import com.walmartlabs.concord.client.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(ParallelRunner.class)
public class CheckpointsIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testCheckpoint() throws Exception {
        // prepare the payload

        byte[] payload = archive(CheckpointsIT.class.getResource("checkpoints").toURI());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Start.*", ab);
        assertLog(".*Middle.*", ab);
        assertLog(".*End.*", ab);

        ProcessEventsApi processEventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> processEvents = processEventsApi.list(pir.getInstanceId(), null, null);
        assertNotNull(processEvents);

        // restore from TWO checkpoint
        String checkpointId = assertCheckpoint("TWO", processEvents);

        ResumeProcessResponse resp = processApi.restore(pir.getInstanceId(), UUID.fromString(checkpointId));
        assertNotNull(resp);

        waitForCompletion(processApi, spr.getInstanceId());

        ab = getLog(pir.getLogFileName());
        assertLog(".*Start.*", ab);
        assertLog(".*Middle.*", ab);
        assertLog(".*End.*", 2, ab);

        // restore from ONE checkpoint
        checkpointId = assertCheckpoint("ONE", processEvents);

        resp = processApi.restore(pir.getInstanceId(), UUID.fromString(checkpointId));
        assertNotNull(resp);

        waitForCompletion(processApi, spr.getInstanceId());
        ab = getLog(pir.getLogFileName());
        assertLog(".*Start.*", ab);
        assertLog(".*Middle.*", 2, ab);
        assertLog(".*End.*", 3, ab);

    }

    @SuppressWarnings("unchecked")
    private static String assertCheckpoint(String name, List<ProcessEventEntry> processEvents) {
        String checkpointDescription = "Checkpoint: " + name;

        for (ProcessEventEntry e : processEvents) {
            Map<String, Object> data = (Map<String, Object>) e.getData();
            if (data == null) {
                continue;
            }
            if ("post".equals(data.get("phase")) && checkpointDescription.equals(data.get("description"))) {
                List<Map<String, Object>> out = (List<Map<String, Object>>) data.get("out");
                if (out == null || out.size() < 2) {
                    continue;
                }

                String checkpointId = assertParam("checkpointId", out.get(0));
                String checkpointName = assertParam("checkpointName", out.get(1));
                if (name.equals(checkpointName)) {
                    return checkpointId;
                }
            }
        }
        throw new IllegalStateException("can't find " + name + " checkpoint");
    }

    private static String assertParam(String paramName, Map<String, Object> params) {
        assertEquals(paramName, params.get("source"));
        assertEquals(paramName, params.get("target"));
        assertNotNull(params.get("resolved"));
        return (String)params.get("resolved");
    }
}
