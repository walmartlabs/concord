package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceTaskIT extends AbstractTest {

    static ConcordRule concord;

    @BeforeAll
    static void setUp(ConcordRule rule) {
        concord = rule;
    }

    @Test
    void testReadAsJson() throws Exception {
        basicAssert(test("resourceReadAsJson"));
    }

    @Test
    void testFromJsonString() throws Exception {
        basicAssert(test("resourceReadFromJsonString"));
    }

    @Test
    void testReadAsString() throws Exception {
        basicAssert(test("resourceReadAsString"));
    }

    @Test
    void testWriteAsJson() throws Exception {
        basicAssert(test("resourceWriteAsJson"));
    }

    @Test
    void testWriteAsString() throws Exception {
        basicAssert(test("resourceWriteAsString"));
    }

    @Test
    void testWriteAsYaml() throws Exception {
        basicAssert(test("resourceWriteAsYaml"));
    }

    @Test
    void testPrintJson() throws Exception {
        ConcordProcess proc = test("resourcePrintJson");

        // ---

        Map<String, Object> out = proc.getOutVariables();

        String condensedResult = (String) out.get("condensedResult");
        assertFalse(condensedResult.contains("\n"));
        assertTrue(condensedResult.contains("\"x\":123"));
        assertTrue(condensedResult.contains("\"y\":\"hello"));

        String prettyResult = (String) out.get("prettyResult");
        assertTrue(prettyResult.contains("\n"));
        assertTrue(prettyResult.contains("\"x\" : 123"));
        assertTrue(prettyResult.contains("\"y\" : \"hello\""));
    }

    private ConcordProcess test(String resource) throws Exception {
        Payload payload = new Payload()
                .archive(resource(resource));

        ConcordProcess proc = concord.processes().start(payload);

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        return proc;
    }

    private void basicAssert(ConcordProcess proc) throws ApiException {
        proc.assertLog(".*Runtime: concord-v2.*");
        proc.assertLog(".*Hello Concord!.*");
    }
}
