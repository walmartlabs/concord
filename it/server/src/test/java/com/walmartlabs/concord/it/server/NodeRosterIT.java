package com.walmartlabs.concord.it.server;

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

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.*;

public class NodeRosterIT extends AbstractServerIT {

    @Rule
    public WireMockRule rule = new WireMockRule(WireMockConfiguration.options()
            .dynamicPort());

    @Before
    public void setUp() {
        rule.stubFor(get(urlEqualTo("/test.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Hello!")));
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testE2e() throws Exception {
        String hostA = "hostA_" + randomString();
        String hostB = "hostB_" + randomString();

        // run an Ansible playbook to get some events

        byte[] payload = archive(ProcessIT.class.getResource("nodeRoster").toURI(), ITConstants.DEPENDENCIES_DIR);

        String artifactUrl = "http://" + env("IT_DOCKER_HOST_ADDR", "localhost") + ":" + rule.port() + "/test.txt";

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.artifactUrl", artifactUrl);
        input.put("arguments.hostA", hostA);
        input.put("arguments.hostB", hostB);
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*" + hostA + ".*failed=0.*", ab);
        assertLog(".*" + hostB + ".*failed=0.*", ab);

        // wait for events to be processes and check for known hosts

        NodeRosterHostsApi hostsApi = new NodeRosterHostsApi(getApiClient());

        UUID hostAId = findHost(hostA, hostsApi);
        UUID hostBId = findHost(hostB, hostsApi);

        // check if the artifact was deployed to our hosts

        NodeRosterArtifactsApi artifactsApi = new NodeRosterArtifactsApi(getApiClient());
        while (true) {
            List<ArtifactEntry> artifactHostsA = artifactsApi.list(hostAId, null, artifactUrl, 1000, 0); // TODO might require paging
            if (artifactHostsA != null && artifactHostsA.size() == 1) {
                break;
            }
        }

        // check if we know who deployed to our hosts

        NodeRosterProcessesApi nrProcessApi = new NodeRosterProcessesApi(getApiClient());
        List<ProcessEntry> hostAProcesses = nrProcessApi.list(hostAId, null, 1000, 0);
        assertFalse(hostAProcesses.isEmpty());
        assertEquals("admin", hostAProcesses.get(0).getInitiator());

        List<ProcessEntry> hostBProcesses = nrProcessApi.list(hostBId, null, 1000, 0);
        assertFalse(hostBProcesses.isEmpty());
        assertEquals("admin", hostBProcesses.get(0).getInitiator());

        // check the host facts
        NodeRosterFactsApi factsApi = new NodeRosterFactsApi(getApiClient());
        assertNotNull(factsApi.getFacts(hostAId, null));
        assertNotNull(factsApi.getFacts(hostBId, null));

        // let's test the task

        payload = archive(ProcessIT.class.getResource("nodeRosterTask").toURI(), ITConstants.DEPENDENCIES_DIR);

        input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.artifactUrl", artifactUrl);
        spr = start(input);

        pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        ab = getLog(pir.getLogFileName());
        assertLog(".*ok=true.*", ab);
        assertLog(".*name: " + hostA.toLowerCase() + ".*", ab);
        assertLog(".*name: " + hostB.toLowerCase() + ".*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testMultipleFactsPerHost() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("nodeRosterMultiFacts").toURI(), ITConstants.DEPENDENCIES_DIR);

        String host = "host_" + randomString();

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.host", host);
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*" + host + ".*failed=0.*", ab);

        // ---

        NodeRosterHostsApi hostsApi = new NodeRosterHostsApi(getApiClient());

        UUID hostId = findHost(host, hostsApi);

        NodeRosterFactsApi factsApi = new NodeRosterFactsApi(getApiClient());
        assertNotNull(factsApi.getFacts(hostId, null));
    }

    private static UUID findHost(String host, NodeRosterHostsApi hostsApi) throws InterruptedException, ApiException {
        while (true) {
            List<HostEntry> l = hostsApi.list(host, null, null, null, 10, 0);
            HostEntry e = l.stream().filter(h -> h.getName().equalsIgnoreCase(host)).findFirst().orElse(null);

            if (e != null) {
                return e.getId();
            }

            Thread.sleep(1000);
        }
    }
}
