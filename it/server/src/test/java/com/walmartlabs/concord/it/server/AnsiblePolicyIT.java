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

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.*;
public class AnsiblePolicyIT extends AbstractServerIT {

    @Test
    public void testTaskDeny() throws Exception {
        // ---

        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        String policyName = "test_policy_" + randomString();
        PolicyEntry policy = new PolicyEntry()
                .name(policyName)
                .rules(readPolicy("ansiblePolicyTaskDeny/test-policy.json"));

        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.createOrUpdatePolicy(policy);
        policyApi.linkPolicy(policyName, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName));

        URI dir = AnsiblePolicyIT.class.getResource("ansiblePolicyTaskDeny").toURI();
        byte[] payload = archive(dir);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(orgName, projectName, null, null, payload);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());

        List<ProcessEventEntry> failedTasks = ansiblePostEvents(pir.getInstanceId()).stream()
                .filter(e -> e.getData() != null
                        && "Copy a local file".equals(e.getData().get("task"))
                        && "FAILED".equals(e.getData().get("status")))
                .toList();
        assertFalse(failedTasks.isEmpty(), "Expected the denied copy to fail");
    }

    @Test
    public void testTemplatedConditions() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        String policyName = "test_policy_" + randomString();
        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.createOrUpdatePolicy(new PolicyEntry()
                .name(policyName)
                .rules(readPolicy("ansiblePolicyTaskDeny/test-policy.json")));
        policyApi.linkPolicy(policyName, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName));

        URI dir = AnsiblePolicyIT.class.getResource("ansiblePolicyTemplatedConditions").toURI();
        StartProcessResponse spr = start(orgName, projectName, null, null, archive(dir));
        ProcessEntry process = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, process.getStatus());

        List<ProcessEventEntry> events = ansiblePostEvents(process.getInstanceId());
        assertTrue(hasTaskStatus(events, "Conditional forbidden copy", "SKIPPED"));
        assertTrue(hasTaskStatus(events, "Copy secret data", "FAILED"));
        assertTrue(hasTaskStatus(events, "Verify policy effects", "OK"));
        assertTrue(hasTaskStatus(events, "Ignored forbidden copy", "FAILED"));
        assertTrue(events.stream()
                .anyMatch(e -> "Ignored forbidden copy".equals(e.getData().get("task"))
                        && Boolean.TRUE.equals(e.getData().get("ignore_errors"))));
    }

    private List<ProcessEventEntry> ansiblePostEvents(UUID instanceId) throws Exception {
        return new ProcessEventsApi(getApiClient())
                .listProcessEvents(instanceId, "ANSIBLE", null, null, null, "post", null, -1);
    }
    private boolean hasTaskStatus(List<ProcessEventEntry> events, String task, String status) {
        return events.stream().anyMatch(e -> e.getData() != null
                && task.equals(e.getData().get("task"))
                && status.equals(e.getData().get("status")));
    }
    private Map<String, Object> readPolicy(String file) throws Exception {
        URL url = AnsiblePolicyIT.class.getResource(file);
        return fromJson(new File(url.toURI()));
    }
}
