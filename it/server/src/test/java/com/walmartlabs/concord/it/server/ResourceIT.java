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

import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import org.junit.Test;

import java.net.URI;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class ResourceIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testReadAsJson() throws Exception {
        test("resourceReadAsJson", ".*Hello Concord!");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testReadAsString() throws Exception {
        test("resourceReadAsString", ".*Hello Concord!");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testWriteAsJson() throws Exception {
        test("resourceWriteAsJson", ".*Hello Concord!");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testWriteAsString() throws Exception {
        test("resourceWriteAsString", ".*Hello Concord!");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testWriteAsYaml() throws Exception {
        test("resourceWriteAsYaml", ".*Hello Concord!");
    }

    private void test(String resource, String pattern) throws Exception {
        URI dir = ResourceIT.class.getResource(resource).toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(pattern, ab);
    }
}
