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

import com.googlecode.junittoolbox.ParallelRunner;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

@RunWith(ParallelRunner.class)
public class ResourceIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testReadAsJson() throws Exception {
        test("resourceReadAsJson", ".*Hello Concord!");
    }

    @Test(timeout = 30000)
    public void testReadAsString() throws Exception {
        test("resourceReadAsString", ".*Hello Concord!");
    }

    @Test(timeout = 30000)
    public void testWriteAsJson() throws Exception {
        test("resourceWriteAsJson", ".*Hello Concord!");
    }

    @Test(timeout = 30000)
    public void testWriteAsString() throws Exception {
        test("resourceWriteAsString", ".*Hello Concord!");
    }

    private void test(String resource, String pattern) throws URISyntaxException, IOException, InterruptedException {
        URI dir = ResourceIT.class.getResource(resource).toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, false, null);

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());
        assertEquals(ProcessStatus.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(pattern, ab);
    }
}
