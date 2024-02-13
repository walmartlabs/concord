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

import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.*;

public class ResourceIT extends AbstractServerIT {

    @Test
    public void testReadAsJson() throws Exception {
        basicAssert(test("resourceReadAsJson"), ".*Hello Concord!");
    }

    @Test
    public void testFromJsonString() throws Exception {
        basicAssert(test("resourceReadFromJsonString"), ".*Hello Concord!");
    }

    @Test
    public void testReadAsString() throws Exception {
        basicAssert(test("resourceReadAsString"), ".*Hello Concord!");
    }

    @Test
    public void testWriteAsJson() throws Exception {
        basicAssert(test("resourceWriteAsJson"), ".*Hello Concord!");
    }

    @Test
    public void testWriteAsString() throws Exception {
        basicAssert(test("resourceWriteAsString"), ".*Hello Concord!");
    }

    @Test
    public void testWriteAsYaml() throws Exception {
        basicAssert(test("resourceWriteAsYaml"), ".*Hello Concord!");
    }

    @Test
    void testPrintJson() throws Exception {
        ProcessEntry pir = test("resourcePrintJson");

        // ---

        Map<String, Object> meta = pir.getMeta();

        String condensedResult = (String) meta.get("condensedResult");
        assertFalse(condensedResult.contains("\n"));
        assertTrue(condensedResult.contains("\"x\":123"));
        assertTrue(condensedResult.contains("\"y\":\"hello"));

        String prettyResult = (String) meta.get("prettyResult");
        assertTrue(prettyResult.contains("\n"));
        assertTrue(prettyResult.contains("\"x\" : 123"));
        assertTrue(prettyResult.contains("\"y\" : \"hello\""));
    }

    private ProcessEntry test(String resource) throws Exception {
        URI dir = ResourceIT.class.getResource(resource).toURI();
        byte[] payload = archive(dir);

        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        return pir;
    }

    private void basicAssert(ProcessEntry pir, @Language("RegExp") String pattern) throws Exception {
        byte[] ab = getLog(pir.getInstanceId());
        assertLog(pattern, ab);
    }
}
