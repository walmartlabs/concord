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

import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.ProcessV2Api;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DispatcherIT extends AbstractServerIT {

    /**
     * Tests the behaviour of the process queue dispatcher when one of
     * the required agent types is not available.
     */
    @Test
    public void testUnknownFlavor() throws Exception {
        byte[] payload = archive(DispatcherIT.class.getResource("unknownFlavor").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("requirements.agent.type", randomString());
        input.put("archive", payload);

        StartProcessResponse unknownFlavor = start(input);

        // ---

        input.put("requirements.agent.type", "test"); // as in it/server/src/test/resources/agent.conf

        StartProcessResponse knownFlavor = start(input);

        ProcessEntry pe = waitForCompletion(getApiClient(), knownFlavor.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        pe = processApi.getProcess(unknownFlavor.getInstanceId(), Collections.emptySet());
        assertEquals(ProcessEntry.StatusEnum.ENQUEUED, pe.getStatus());

        new ProcessApi(getApiClient()).kill(pe.getInstanceId());
    }
}
