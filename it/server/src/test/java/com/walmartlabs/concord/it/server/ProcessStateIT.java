package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class ProcessStateIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testSingleFile() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("stateSingleFile").toURI());

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pe = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pe.getStatus());

        // ---

        Response resp = processResource.downloadState(spr.getInstanceId(), "concord.yml");
        assertEquals(200, resp.getStatus());
    }
}
