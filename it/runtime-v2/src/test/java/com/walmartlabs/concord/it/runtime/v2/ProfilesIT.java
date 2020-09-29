package com.walmartlabs.concord.it.runtime.v2;

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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import com.walmartlabs.concord.client.ProcessEntry;
import org.junit.ClassRule;
import org.junit.Test;

import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.Assert.assertEquals;

public class ProfilesIT {

    @ClassRule
    public static final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * Override flows from active profiles.
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testFlowOverride() throws Exception {
        Payload payload = new Payload()
                .activeProfiles("stranger")
                .archive(ProfilesIT.class.getResource("profiles").toURI());

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        proc.assertLog(".*From profile: Hello, stranger.*");
    }
}
