package com.walmartlabs.concord.it.runtime.v2;

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
import com.walmartlabs.concord.client2.HostEntry;
import com.walmartlabs.concord.client2.NodeRosterHostsApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.it.common.Version;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.walmartlabs.concord.it.runtime.v2.Utils.resourceToString;

public class NodeRosterIT extends AbstractTest {

    static ConcordRule concord;

    @BeforeAll
    static void setUp(ConcordRule rule) {
        concord = rule;
    }

    /**
     * Tests various methods of the 'noderoster' plugin.
     */
    @Test
    public void test() throws Exception {
        // run the Ansible flow first to get some data

        String concordYml = resourceToString(NodeRosterIT.class.getResource("noderoster/ansible.yml"))
                .replaceAll("PROJECT_VERSION", Version.PROJECT_VERSION);

        ConcordProcess proc = concord.processes().start(new Payload()
                .concordYml(concordYml)
                .resource("playbook.yml", NodeRosterIT.class.getResource("noderoster/playbook.yml")));

        ProcessEntry pe = expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // wait for the Node Roster data to appear

        NodeRosterHostsApi hostsApi = new NodeRosterHostsApi(concord.apiClient());
        while (true) {
            List<HostEntry> l = hostsApi.listKnownHosts(null, null, pe.getInstanceId(), null, 10, 0);
            if (!l.isEmpty()) {
                break;
            }
        }

        // run the Node Roster flow next to test the plugin

        concordYml = resourceToString(ProcessIT.class.getResource("noderoster/noderoster.yml"))
                .replaceAll("PROJECT_VERSION", Version.PROJECT_VERSION);

        proc = concord.processes().start(new Payload()
                .concordYml(concordYml));

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*hostsWithArtifacts:.*ok=true.*");
        proc.assertLog(".*ansible_dns=.*");
        proc.assertLog(".*deployedOnHost:.*ok=true.*");
    }
}
