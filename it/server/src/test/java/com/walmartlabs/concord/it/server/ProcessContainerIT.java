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

import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import com.walmartlabs.concord.common.PathUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Requires configuration changes for ITs")
public class ProcessContainerIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        Path src = Paths.get(ProcessContainerIT.class.getResource("processContainer").toURI());
        Path dst = PathUtils.createTempDir("test");
        PathUtils.copy(src, dst);

        Path concordYml = dst.resolve("concord.yml");
        String s = new String(Files.readAllBytes(concordYml));
        s = s.replaceAll("%%image%%", ITConstants.DOCKER_ANSIBLE_IMAGE);
        Files.write(concordYml, s.getBytes());

        // ---

        byte[] payload = archive(dst.toUri());

        StartProcessResponse spr = start(payload);

        // --

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pir.getLogFileName());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello from Docker!.*", ab);
    }
}
