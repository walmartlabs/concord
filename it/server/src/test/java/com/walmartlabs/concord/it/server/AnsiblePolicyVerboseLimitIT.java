package com.walmartlabs.concord.it.server;

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

import com.walmartlabs.concord.client.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnsiblePolicyVerboseLimitIT extends AbstractServerIT {

    private String orgName;
    private String projectName;

    @BeforeEach
    public void setup() throws Exception {

        // -- Add policy to restrict verbose logging

        orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        Map<String, Object> ansibleVerboseLimits = new HashMap<>();
        ansibleVerboseLimits.put("maxHosts", 1);
        ansibleVerboseLimits.put("maxTotalWork", 2);

        String policyName = "policy_" + randomString();
        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.createOrUpdate(new PolicyEntry()
                .setName(policyName)
                .setRules(singletonMap("processCfg",
                        singletonMap("arguments",
                                singletonMap("ansibleVerboseLimits",
                                        ansibleVerboseLimits)))));

        policyApi.link(policyName, new PolicyLinkEntry()
                .setOrgName(orgName)
                .setProjectName(projectName));
    }

    @Test
    public void testLargeInventoryLimitedToGroup() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleLargeVerbose").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.playbook", "playbook_single.yml");
        input.put("arguments.verboseLevel", "1");
        input.put("arguments.invFile", "inventory_limit.ini");
        input.put("arguments.groupLimit", "dev");
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus(), "Large inventory limited to small group must FINISH");

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*ansible completed successfully.*", ab);
    }

    @Test
    public void testVerboseTooManyImportedTasks() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleLargeVerbose").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.playbook", "playbook_include.yml");
        input.put("arguments.verboseLevel", "4");
        input.put("arguments.invFile", "inventory_small.ini");
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus(),
                "Imported tasks exceeding max work must FINISH");

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Disabling verbose output. Too much work.*", ab);
    }

    @Test
    public void testVerboseTooManyHosts() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleLargeVerbose").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.playbook", "playbook_single.yml");
        input.put("arguments.verboseLevel", "1");
        input.put("arguments.invFile", "inventory_large.ini");
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus(),
                "Large inventory with verbose logging must FINISH");

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Disabling verbose output. Too many hosts.*", ab);
    }

    @Test
    public void testVerboseTooMuchWork() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleLargeVerbose").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.playbook", "playbook_multi.yml");
        input.put("arguments.verboseLevel", "1");
        input.put("arguments.invFile", "inventory_small.ini");
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus(),
                "Small inventory with many calls and verbose logging must FINISH");

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Disabling verbose output. Too much work.*", ab);
    }

    @Test
    public void testNoVerboseLargeInventory() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleLargeVerbose").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.playbook", "playbook_single.yml");
        input.put("arguments.verboseLevel", "0");
        input.put("arguments.invFile", "inventory_large.ini");
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus(),
                "Large inventory with standard logging must FINISH");

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*ansible completed successfully.*", ab);
    }

    @Test
    public void testVerboseSmallInventory() throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleLargeVerbose").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.playbook", "playbook_single.yml");
        input.put("arguments.verboseLevel", "3");
        input.put("arguments.invFile", "inventory_small.ini");
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus(),
                "Small inventory with verbose logging must FINISH");

        // ---

        byte[] ab = getLog(pir.getLogFileName());
        // only shows with verbose logging enabled
        // TODO may be flaky? no guarantee it'll *always* be in every ansible version
        assertLog(".*Using .* as config file.*", ab);
    }
}
