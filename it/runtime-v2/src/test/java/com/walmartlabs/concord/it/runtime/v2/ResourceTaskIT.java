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
import com.walmartlabs.concord.client.ProcessEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class ResourceTaskIT {

    @RegisterExtension
    public static final ConcordRule concord = ConcordConfiguration.configure();

    @Test
    public void testReadAsJson() throws Exception {
        test("resourceReadAsJson");
    }

    @Test
    public void testFromJsonString() throws Exception {
        test("resourceReadFromJsonString");
    }

    @Test
    public void testReadAsString() throws Exception {
        test("resourceReadAsString");
    }

    @Test
    public void testWriteAsJson() throws Exception {
        test("resourceWriteAsJson");
    }

    @Test
    public void testWriteAsString() throws Exception {
        test("resourceWriteAsString");
    }

    @Test
    public void testWriteAsYaml() throws Exception {
        test("resourceWriteAsYaml");
    }

    private void test(String resource) throws Exception {
        Payload payload = new Payload()
                .archive(ResourceTaskIT.class.getResource(resource).toURI());

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        proc.assertLog(".*Runtime: concord-v2.*");
        proc.assertLog(".*Hello Concord!.*");
    }
}
