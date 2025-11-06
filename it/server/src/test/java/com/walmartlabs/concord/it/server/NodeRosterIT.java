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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NodeRosterIT extends AbstractServerIT {

    @RegisterExtension
    static WireMockExtension rule = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .dynamicPort())
            .build();

    @BeforeEach
    public void setUp() {
        rule.stubFor(get(urlEqualTo("/test.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Hello!")));
    }

    @Test
    public void testE2e() throws Exception {
        String hostA = "hostA_" + randomString();
        String hostB = "hostB_" + randomString();

        // run an Ansible playbook to get some events

        byte[] payload = archive(NodeRosterIT.class.getResource("nodeRoster").toURI());

        String artifactUrl = "http://" + env("IT_DOCKER_HOST_ADDR", "localhost") + ":" + rule.getPort() + "/test.txt";

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.artifactUrl", artifactUrl);
        input.put("arguments.hostA", hostA);
        input.put("arguments.hostB", hostB);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*" + hostA + ".*failed=0.*", ab);
        assertLog(".*" + hostB + ".*failed=0.*", ab);

        // wait for events to be processes and check for known hosts

        NodeRosterHostsApi hostsApi = new NodeRosterHostsApi(getApiClient());

        UUID hostAId = findHost(hostA, hostsApi);
        UUID hostBId = findHost(hostB, hostsApi);

        // check if the artifact was deployed to our hosts

        NodeRosterArtifactsApi artifactsApi = new NodeRosterArtifactsApi(getApiClient());
        while (true) {
            List<ArtifactEntry> artifactHostsA = artifactsApi.listHostArtifacts(hostAId, null, artifactUrl, 1000, 0); // TODO might require paging
            if (artifactHostsA != null && artifactHostsA.size() == 1) {
                break;
            }
        }

        // check if we know who deployed to our hosts

        List<ProcessEntry> hostAProcesses = listHostProcesses(hostAId);
        assertEquals("admin", hostAProcesses.get(0).getInitiator());

        List<com.walmartlabs.concord.client2.ProcessEntry> hostBProcesses = listHostProcesses(hostBId);
        assertEquals("admin", hostBProcesses.get(0).getInitiator());

        // check the host facts
        NodeRosterFactsApi factsApi = new NodeRosterFactsApi(getApiClient());
        assertNotNull(factsApi.getFacts(hostAId, null));
        assertNotNull(factsApi.getFacts(hostBId, null));

        // let's test the task

        payload = archive(ProcessIT.class.getResource("nodeRosterTask").toURI());

        input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.artifactUrl", artifactUrl);
        spr = start(input);

        pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        ab = getLog(pir.getInstanceId());
        assertLog(".*ok=true.*", ab);
        assertLog(".*name: " + hostA.toLowerCase() + ".*", ab);
        assertLog(".*name: " + hostB.toLowerCase() + ".*", ab);
    }

    @Test
    public void testMultipleFactsPerHost() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("nodeRosterMultiFacts").toURI());

        String host = "host_" + randomString();

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.host", host);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*" + host + ".*failed=0.*", ab);

        // ---

        NodeRosterHostsApi hostsApi = new NodeRosterHostsApi(getApiClient());

        UUID hostId = findHost(host, hostsApi);
        assertNotNull(getFacts(hostId));
    }

    private static UUID findHost(String host, NodeRosterHostsApi hostsApi) throws InterruptedException, ApiException {
        while (true) {
            List<HostEntry> l = hostsApi.listKnownHosts(host, null, null, null, 10, 0);
            HostEntry e = l.stream().filter(h -> h.getName().equalsIgnoreCase(host)).findFirst().orElse(null);

            if (e != null) {
                return e.getId();
            }

            Thread.sleep(1000);
        }
    }

    private List<com.walmartlabs.concord.client2.ProcessEntry> listHostProcesses(UUID hostAId) throws Exception {
        NodeRosterProcessesApi nrProcessApi = new NodeRosterProcessesApi(getApiClient());
        while (true) {
            List<com.walmartlabs.concord.client2.ProcessEntry> result = nrProcessApi.listHosts(hostAId, null, 1000, 0);
            if (!result.isEmpty()) {
                return result;
            }

            Thread.sleep(1000);
        }
    }

    private Object getFacts(UUID hostId) throws Exception {
        NodeRosterFactsApi factsApi = new NodeRosterFactsApi(getApiClient());
        while (true) {
            Object facts = factsApi.getFacts(hostId, null);
            if (facts != null) {
                return facts;
            }
            
            Thread.sleep(1000);
        }
    }
}
