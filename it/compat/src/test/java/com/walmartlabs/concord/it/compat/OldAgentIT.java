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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.ProcessEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.images.PullPolicy;

/**
 * Runs an older version of the Agent with the current version of the Server.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_OLD_AGENT_TESTS", matches = "true", disabledReason = "Requires a published agent image of the old version.")
public class OldAgentIT {

    @RegisterExtension
    public final ConcordRule concord = new ConcordRule()
            .serverImage(System.getProperty("server.image", "walmartlabs/concord-server"))
            .agentImage(System.getProperty("agent.image", "walmartlabs/concord-agent"))
            .pullPolicy(PullPolicy.defaultPolicy())
            .streamServerLogs(true)
            .streamAgentLogs(true);

    @Test
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
