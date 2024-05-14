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
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PolicyIT extends AbstractServerIT {

    @Test
    public void testCfg() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        String policyName = "policy_" + randomString();
        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.createOrUpdatePolicy(new PolicyEntry()
                .name(policyName)
                .rules(singletonMap("processCfg",
                        singletonMap("arguments",
                                singletonMap("x",
                                        singletonMap("name", "Concord"))))));

        policyApi.linkPolicy(policyName, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("policyCfg").toURI());

        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Hello, Stranger.*", ab);

        // ---

        spr = start(orgName, projectName, null, null, payload);

        pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        ab = getLog(pir.getInstanceId());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test
    public void testMaxForkDepth() throws Exception {
        String orgName = createOrg();
        String projectName = createProject(orgName);

        Map<String, Object> queueRules = new HashMap<>();
        queueRules.put("forkDepth", singletonMap("max", 2));

        Map<String, Object> rules = singletonMap("queue", queueRules);

        createPolicy(orgName, projectName, rules);

        // ---
        byte[] payload = archive(ProcessIT.class.getResource("forkDepth").toURI());

        StartProcessResponse spr = start(orgName, projectName, null, null, payload);

        waitForStatus(getApiClient(), spr.getInstanceId(), StatusEnum.RUNNING);

        waitForCompletion(getApiClient(), spr.getInstanceId());
    }

    @Test
    public void testConcurrentProcess() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        String policyName = "policy_" + randomString();
        PolicyApi policyApi = new PolicyApi(getApiClient());
        Map<String, Object> queueRules = new HashMap<>();
        queueRules.put("concurrent", singletonMap("max", 1));

        Map<String, Object> rules = singletonMap("queue", queueRules);
        policyApi.createOrUpdatePolicy(new PolicyEntry()
                .name(policyName)
                .rules(rules));

        policyApi.linkPolicy(policyName, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("withDelay").toURI());

        Map<String, Object> input1 = new HashMap<>();
        input1.put("archive", payload);
        input1.put("org", orgName);
        input1.put("project", projectName);
        input1.put("request", singletonMap("arguments", singletonMap("delayValue", 10000)));

        StartProcessResponse spr1 = start(input1);

        waitForStatus(getApiClient(), spr1.getInstanceId(), StatusEnum.RUNNING);

        // --- start second process

        Map<String, Object> input2 = new HashMap<>(input1);
        input2.put("request", singletonMap("arguments", singletonMap("delayValue", 0)));

        StartProcessResponse spr2 = start(input2);

        // ---

        waitForCompletion(getApiClient(), spr1.getInstanceId());

        // ---

        waitForCompletion(getApiClient(), spr2.getInstanceId());
    }

    @Test
    public void testConcurrentWithSuspendedProcess() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        String policyName = "policy_" + randomString();
        PolicyApi policyApi = new PolicyApi(getApiClient());
        Map<String, Object> queueRules = new HashMap<>();
        queueRules.put("concurrent", singletonMap("max", 1));

        Map<String, Object> rules = singletonMap("queue", queueRules);
        policyApi.createOrUpdatePolicy(new PolicyEntry()
                .name(policyName)
                .rules(rules));

        policyApi.linkPolicy(policyName, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName));

        // ---

        byte[] payload = archive(ProcessIT.class.getResource("withForm").toURI());

        Map<String, Object> input1 = new HashMap<>();
        input1.put("archive", payload);
        input1.put("org", orgName);
        input1.put("project", projectName);
        input1.put("request", singletonMap("arguments", singletonMap("delayValue", 10000)));

        StartProcessResponse spr1 = start(input1);

        waitForStatus(getApiClient(), spr1.getInstanceId(), StatusEnum.RUNNING);

        // --- start the second process

        byte[] payload2 = archive(ProcessIT.class.getResource("withDelay").toURI());
        Map<String, Object> input2 = new HashMap<>();
        input2.put("archive", payload2);
        input2.put("org", orgName);
        input2.put("project", projectName);
        input2.put("request", singletonMap("arguments", singletonMap("delayValue", 0)));

        StartProcessResponse spr2 = start(input2);

        // ---

        waitForCompletion(getApiClient(), spr2.getInstanceId());
    }

    @Test
    public void testMaxProcessTimeout() throws Exception {
        String orgName = createOrg();
        String projectName = createProject(orgName);

        Map<String, Object> queueRules = new HashMap<>();
        queueRules.put("processTimeout", singletonMap("max", "PT1M"));

        Map<String, Object> rules = singletonMap("queue", queueRules);

        createPolicy(orgName, projectName, rules);

        // ---
        byte[] payload = archive(ProcessIT.class.getResource("process").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("request", singletonMap("processTimeout", "PT10M"));

        StartProcessResponse spr = start(input);

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(StatusEnum.FAILED, pe.getStatus());

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*Maximum processTimeout value exceeded.*", ab);
    }

    private String createOrg() throws ApiException {
        String orgName = "org_" + randomString();

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        return orgName;
    }

    private String createProject(String orgName) throws ApiException {
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        return projectName;
    }

    private String createPolicy(String orgName, String projectName, Map<String, Object> rules) throws ApiException {
        String policyName = "policy_" + randomString();

        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.createOrUpdatePolicy(new PolicyEntry()
                .name(policyName)
                .rules(rules));

        policyApi.linkPolicy(policyName, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName));

        return policyName;
    }
}
