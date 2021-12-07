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

import com.walmartlabs.concord.client.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertNotNull;

public class DefaultProcessVariablesIT extends AbstractServerIT {

    private static final String POLICY_NAME = "default-vars-from-test";

    @Before
    public void precondition() throws Exception {
        Map<String, Object> defVars = new HashMap<>();
        defVars.put("var1", "value1");
        defVars.put("var2", "value2");

        PolicyApi policyApi = new PolicyApi(getApiClient());
        PolicyEntry policy = new PolicyEntry();
        policy.setName(POLICY_NAME);
        policy.setRules(Collections.singletonMap("defaultProcessCfg", Collections.singletonMap("defaultTaskVariables",
                Collections.singletonMap("testDefaultVars", defVars))));

        policyApi.createOrUpdate(policy);
        PolicyLinkEntry link = new PolicyLinkEntry();
        policyApi.link(POLICY_NAME, link);
        policyApi.refresh();
    }

    @After
    public void cleanup() {
        PolicyApi policyApi = new PolicyApi(getApiClient());
        try {
            policyApi.delete(POLICY_NAME);
        } catch (Exception e) {
            // ignore
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testDefaultVarsAccess() throws Exception {
        // prepare the payload

        byte[] payload = archive(ProcessIT.class.getResource("defaultVars").toURI());

        // start the process

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);
        assertNotNull(spr.getInstanceId());

        // wait for completion

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

        // get the name of the agent's log file

        assertNotNull(pir.getLogFileName());

        // check the logs

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*Default vars: value1.*", ab);
    }
}
