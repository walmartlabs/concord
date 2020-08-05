package com.walmartlabs.concord.it.compat;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.Concord;
import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import com.walmartlabs.concord.client.ProcessEntry;
import org.junit.Rule;
import org.junit.Test;

import static com.walmartlabs.concord.it.compat.ITConstants.DEFAULT_TEST_TIMEOUT;

/**
 * Runs the current versions of the Server and the Agent in testcontainer-concord's LOCAL mode.
 */
public class LocalModeIT {

    @Rule
    public ConcordRule concord = new ConcordRule()
            .mode(Concord.Mode.LOCAL);

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        String concordYml = "flows:\n" +
                "  default:\n" +
                "    - log: \"Hello!\"\n";

        ConcordProcess proc = concord.processes().start(new Payload()
                .concordYml(concordYml));

        proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        proc.assertLog(".*Hello!.*");
    }
}
