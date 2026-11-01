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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.*;

public class ResourceIT extends AbstractServerIT {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "resourceReadAsJson",
            "resourceReadFromJsonString",
            "resourceReadAsString",
            "resourceWriteAsJson",
            "resourceWriteAsString",
            "resourceWriteAsYaml"
    })
    public void testResource(String resource) throws Exception {
        ProcessEntry pir = test(resource);
        assertLog(".*Hello Concord!", getLog(pir.getInstanceId()));
    }

    @Test
    void testPrintJson() throws Exception {
        ProcessEntry pir = test("resourcePrintJson");

        // ---

        Map<String, Object> meta = pir.getMeta();
        ObjectMapper mapper = getApiClient().getObjectMapper();

        String condensedResult = (String) meta.get("condensedResult");
        assertFalse(condensedResult.contains("\n"));
        assertEquals(Map.of("x", 123, "y", "hello"),
                mapper.readValue(condensedResult, Map.class));

        String prettyResult = (String) meta.get("prettyResult");
        assertTrue(prettyResult.contains("\n"));
        assertEquals(Map.of("x", 123, "y", "hello"),
                mapper.readValue(prettyResult, Map.class));
    }

    private ProcessEntry test(String resource) throws Exception {
        URI dir = ResourceIT.class.getResource(resource).toURI();
        byte[] payload = archive(dir);

        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        return pir;
    }
}
