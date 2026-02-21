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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledIfEnvironmentVariable(named = "SKIP_DOCKER_TESTS", matches = "true", disabledReason = "Requires dockerd listening on a tcp socket. Not available in a typical CI environment")
public class DockerAnsibleIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        byte[] payload = archive(DockerAnsibleIT.class.getResource("dockerAnsible").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.image", ITConstants.DOCKER_ANSIBLE_IMAGE);
        StartProcessResponse spr = start(input);

        // --

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pir.getLogFileName());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*\"msg\": \"Hello from Docker!\".*", ab);

        // --

        ProcessApi processApi = new ProcessApi(getApiClient());
        try (InputStream is = processApi.downloadAttachment(pir.getInstanceId(), "ansible_stats.json")) {
            assertNotNull(is);
        }
    }
}
