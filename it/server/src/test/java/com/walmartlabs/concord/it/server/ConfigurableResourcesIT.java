package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import org.junit.jupiter.api.Test;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigurableResourcesIT extends AbstractServerIT {

    @Test
    public void testProfiles() throws Exception {
        // prepare the payload

        byte[] payload = archive(ConfigurableResourcesIT.class.getResource("configurableProfilesDirectory").toURI());

        // start the process

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello, world.*", ab);
    }

    @Test
    public void testFlows() throws Exception {
        // prepare the payload

        byte[] payload = archive(ConfigurableResourcesIT.class.getResource("configurableFlowsDirectory").toURI());

        // start the process

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*External flow!.*", ab);
    }

    @Test
    public void testDisabledProfiles() throws Exception {
        // prepare the payload

        byte[] payload = archive(ConfigurableResourcesIT.class.getResource("disableProfilesDirectory").toURI());

        // start the process

        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*Hello, stranger.*", ab);
    }

    @Test
    public void testInvalidDir() throws Exception {
        byte[] payload = archive(ConfigurableResourcesIT.class.getResource("invalidResourcesPath").toURI());

        // ---

        try {
            start(payload);
        } catch (ApiException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("../../etc"));
        }
    }
}
