package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PublicFlowsIT extends AbstractServerIT {

    /**
     * Verifies that {@code publicFlow} values are merged across profiles
     * and a {@code publicFlow} from a profile can be used an the {@code entryPoint}
     */
    @Test
    public void testProfiles() throws Exception {
        byte[] payload = archive(PublicFlowsIT.class.getResource("publicFlowsInProfiles").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        byte[] ab = getLog(pe.getInstanceId());

        assertLog(".*Hello A.*", ab);
        assertLog(".*Hello B.*", ab);
        assertLog(".*Hello C.*", ab);

        // ---

        input = new HashMap<>();
        input.put("archive", payload);
        input.put("activeProfiles", "profileA");
        input.put("entryPoint", "flowA");

        spr = start(input);

        pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        ab = getLog(pe.getInstanceId());

        assertLog(".*Hello A.*", ab);
        assertNoLog(".*Hello B.*", ab);
        assertNoLog(".*Hello C.*", ab);

        // ---

        input = new HashMap<>();
        input.put("archive", payload);
        input.put("entryPoint", "flowC");

        spr = start(input);

        pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pe.getStatus());

        ab = getLog(pe.getInstanceId());

        assertLogAtLeast(".*not a public flow.*", 1, ab);
    }
}
