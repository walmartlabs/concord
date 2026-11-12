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

import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.*;

public class AnsibleEventIT extends AbstractServerIT {

    private static final String LIFECYCLE_SENTINEL = "upgrade-private-sentinel";

    @Test
    public void testEventLifecycleLocal() throws Exception {
        assertEventLifecycle(false);
    }

    @Test
    public void testEventLifecycleDocker() throws Exception {
        assertEventLifecycle(true);
    }

    @SuppressWarnings("unchecked")
    private void assertEventLifecycle(boolean docker) throws Exception {
        URI uri = AnsibleEventIT.class.getResource("ansibleEventLifecycle").toURI();
        byte[] payload = archive(uri);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        if (docker) {
            input.put("arguments.image", ITConstants.DOCKER_ANSIBLE_IMAGE);
        }
        StartProcessResponse spr = start(input);
        UUID instanceId = spr.getInstanceId();

        ProcessEntry process = waitForCompletion(getApiClient(), instanceId);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, process.getStatus());

        byte[] log = getLog(instanceId);
        assertLog(".*message: upgrade-ok.*", log);
        assertLog(".*count: 7.*", log);
        assertLog(".*unicode: héllo.*", log);
        assertLog(".*exitCode: 0.*", log);
        assertLog(".*\\[INFO \\] ok:.*127.0.0.1.*", log);
        assertFalse(new String(log).contains(LIFECYCLE_SENTINEL));

        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> events = eventsApi.listProcessEvents(instanceId, "ANSIBLE", null, null, null, null, null, -1);
        assertFalse(events.isEmpty());

        Map<String, List<ProcessEventEntry>> taskEvents = events.stream()
                .filter(e -> e.getData() != null && e.getData().get("correlationId") != null
                        && e.getData().get("task") != null)
                .collect(Collectors.groupingBy(e -> e.getData().get("correlationId").toString()));
        Map<String, String> statuses = Map.of(
                "Timed task", "OK",
                "Export nested output", "OK",
                "Skipped task", "SKIPPED",
                "Ignored failure", "FAILED",
                "Private task", "OK");
        for (Map.Entry<String, String> expected : statuses.entrySet()) {
            List<List<ProcessEventEntry>> matching = taskEvents.values().stream()
                    .filter(group -> group.stream().anyMatch(e -> expected.getKey().equals(e.getData().get("task"))))
                    .toList();
            assertEquals(1, matching.size(), "Expected one task correlation for " + expected.getKey());
            List<ProcessEventEntry> paired = matching.get(0);
            assertEquals(2, paired.size(), "Expected a pre/post event pair for " + expected.getKey());
            ProcessEventEntry pre = paired.stream()
                    .filter(e -> "pre".equals(e.getData().get("phase")))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing pre event for " + expected.getKey()));
            ProcessEventEntry post = paired.stream()
                    .filter(e -> "post".equals(e.getData().get("phase")))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing post event for " + expected.getKey()));
            Map<String, Object> before = pre.getData();
            Map<String, Object> after = post.getData();
            assertEquals(expected.getKey(), before.get("task"));
            assertEquals(expected.getKey(), after.get("task"));
            assertEquals("RUNNING", before.get("status"));
            for (String field : List.of("taskId", "host", "playbookId", "parentCorrelationId")) {
                assertNotNull(before.get(field), "Missing " + field + " in pre event for " + expected.getKey());
                assertEquals(before.get(field), after.get(field), "Mismatched " + field + " for " + expected.getKey());
            }
            assertEquals("127.0.0.1", after.get("host"));
            assertEquals(expected.getValue(), after.get("status"));
            if ("Timed task".equals(expected.getKey())) {
                assertTrue(after.get("duration") instanceof Number);
                assertTrue(((Number) after.get("duration")).longValue() > 0);
            }
            if ("Ignored failure".equals(expected.getKey())) {
                assertEquals(Boolean.TRUE, after.get("ignore_errors"));
            }
            if ("Private task".equals(expected.getKey())) {
                Map<String, Object> result = (Map<String, Object>) after.get("result");
                assertNotNull(result, "Private task result should be censored, not omitted");
                assertTrue(result.containsKey("censored"), "Expected a censored private task result: " + result);
            }
        }
        String serializedEvents = events.stream().map(e -> e.getData().toString()).collect(Collectors.joining());
        assertFalse(serializedEvents.contains(LIFECYCLE_SENTINEL));

        List<ProcessEventEntry> playbookResults = eventsApi.listProcessEvents(instanceId,
                "ANSIBLE_PLAYBOOK_RESULT", null, null, null, null, null, -1);
        assertEquals(1, playbookResults.size());
        assertEquals("OK", playbookResults.get(0).getData().get("status"));

        ProcessApi processApi = new ProcessApi(getApiClient());
        try (InputStream response = processApi.downloadAttachment(instanceId, "ansible_stats.json")) {
            Map<String, Object> stats = fromJson(response);
            assertEquals(0, ((Number) stats.get("exitCode")).intValue());
            assertStatsIncludeSuccessfulHost(stats);
        }
        try (InputStream response = processApi.downloadAttachment(instanceId, "ansible_stats_v2.json")) {
            List<Map<String, Object>> stats = fromJson(response, List.class);
            assertEquals(1, stats.size());
            assertEquals(0, ((Number) stats.get(0).get("exitCode")).intValue());
            assertStatsIncludeSuccessfulHost((Map<String, Object>) stats.get(0).get("stats"));
        }

        AnsibleProcessApi ansibleApi = new AnsibleProcessApi(getApiClient());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        List<AnsibleHostEntry> hostEntries = List.of();
        while (System.nanoTime() < deadline) {
            AnsibleHostListResponse response = ansibleApi.listAnsibleHosts(instanceId, null, null, null, null, null, 30, 0, null, null);
            hostEntries = response == null || response.getItems() == null ? List.of() : response.getItems();
            AnsibleHostEntry host = hostEntries.stream()
                    .filter(h -> "127.0.0.1".equals(h.getHost()))
                    .findFirst().orElse(null);
            if (host != null && host.getDuration() > 0 && "OK".equals(host.getStatus().toString())) {
                return;
            }
            Thread.sleep(500);
        }
        fail("Host summary did not reach positive-duration OK status; last response: " + hostEntries);
    }

    private static void assertStatsIncludeSuccessfulHost(Map<String, Object> stats) {
        assertNotNull(stats);
        for (String key : List.of("ok", "failures", "unreachable")) {
            List<?> hosts = assertInstanceOf(List.class, stats.get(key));
            assertEquals("ok".equals(key), hosts.contains("127.0.0.1"),
                    "Unexpected hosts in " + key + ": " + hosts);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEvent() throws Exception {
        URI uri = AnsibleEventIT.class.getResource("ansibleEvent").toURI();
        byte[] payload = archive(uri);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> l = eventsApi.listProcessEvents(pir.getInstanceId(), null, null, null, null, null, null, -1);
        assertFalse(l.isEmpty());

        long cnt = l.stream().filter(e -> {
            if (e.getData() == null) {
                return false;
            }

            Map<String, Object> m = e.getData();

            Map<String, Object> r = (Map<String, Object>) m.get("result");
            if (r == null) {
                return false;
            }

            String msg = (String) r.get("msg");
            if (msg == null) {
                return false;
            }

            return msg.equals("Hi there!");
        }).count();

        assertEquals(1, cnt);
    }

    @Test
    public void testIgnoredFailures() throws Exception {
        URI uri = AnsibleEventIT.class.getResource("ansibleIgnoredFailures").toURI();
        byte[] payload = archive(uri);

        // ---

        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> l = eventsApi.listProcessEvents(pir.getInstanceId(), null, null, null, null, null, null, -1);
        assertFalse(l.isEmpty());

        long cnt = l.stream().filter(e -> {
            if (e.getData() == null) {
                return false;
            }

            Map<String, Object> m = e.getData();

            Object ignoreErrors = m.get("ignore_errors");
            if (ignoreErrors == null) {
                return false;
            }

            return Boolean.TRUE.equals(ignoreErrors);
        }).count();

        assertEquals(1, cnt);
    }

    /**
     * Runs a playbook that fails on one of the steps.
     * Verifies that failed host events are correctly recorded.
     */
    @Test
    public void testFailedHosts() throws Exception {
        URI uri = AnsibleEventIT.class.getResource("ansibleFailedHosts").toURI();
        byte[] payload = archive(uri);

        // ---

        StartProcessResponse spr = start(payload);

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pe.getStatus());

        // ---

        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());
        List<ProcessEventEntry> l = eventsApi.listProcessEvents(pe.getInstanceId(), "ANSIBLE", null, null, null, "post", null, -1);
        assertFalse(l.isEmpty());

        for (ProcessEventEntry e : l) {
            Map<String, Object> m = e.getData();
            if (m.get("task").equals("fail") && m.get("status").equals("FAILED")) {
                return;
            }
        }

        fail("Can't find the required events");
    }
}
