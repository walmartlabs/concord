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

import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

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
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        Map<String, Object> ansibleVerboseLimits = new HashMap<>();
        ansibleVerboseLimits.put("maxHosts", 1);
        ansibleVerboseLimits.put("maxTotalWork", 2);

        String policyName = "policy_" + randomString();
        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.createOrUpdatePolicy(new PolicyEntry()
                .name(policyName)
                .rules(singletonMap("processCfg",
                        singletonMap("arguments",
                                singletonMap("ansibleVerboseLimits",
                                        ansibleVerboseLimits)))));

        policyApi.linkPolicy(policyName, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName));
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> scenarios() {
        return Stream.of(
                // a group limit keeps a large inventory under the policy limits
                Arguments.of("large inventory limited to a small group",
                        "playbook_single.yml", "1", "inventory_limit.ini", "dev",
                        "Large inventory limited to small group must FINISH",
                        ".*ansible completed successfully.*"),
                // work of imported playbooks counts towards maxTotalWork
                Arguments.of("imported tasks exceeding max work",
                        "playbook_include.yml", "4", "inventory_small.ini", null,
                        "Imported tasks exceeding max work must FINISH",
                        ".*Disabling verbose output. Too much work.*"),
                Arguments.of("too many hosts",
                        "playbook_single.yml", "1", "inventory_large.ini", null,
                        "Large inventory with verbose logging must FINISH",
                        ".*Disabling verbose output. Too many hosts.*"),
                Arguments.of("too much work",
                        "playbook_multi.yml", "1", "inventory_small.ini", null,
                        "Small inventory with many calls and verbose logging must FINISH",
                        ".*Disabling verbose output. Too much work.*"),
                // verbose logging disabled by the flow, not by the policy
                Arguments.of("large inventory without verbose logging",
                        "playbook_single.yml", "0", "inventory_large.ini", null,
                        "Large inventory with standard logging must FINISH",
                        ".*ansible completed successfully.*"),
                // only shows with verbose logging enabled
                // TODO may be flaky? no guarantee it'll *always* be in every ansible version
                Arguments.of("small inventory with verbose logging",
                        "playbook_single.yml", "3", "inventory_small.ini", null,
                        "Small inventory with verbose logging must FINISH",
                        ".*Using .* as config file.*"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    public void test(String displayName, String playbook, String verboseLevel, String invFile, String groupLimit,
                     String mustFinishMessage, String expectedLog) throws Exception {
        URI dir = AnsibleIT.class.getResource("ansibleLargeVerbose").toURI();
        byte[] payload = archive(dir);

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.playbook", playbook);
        input.put("arguments.verboseLevel", verboseLevel);
        input.put("arguments.invFile", invFile);
        if (groupLimit != null) {
            input.put("arguments.groupLimit", groupLimit);
        }
        input.put("archive", payload);

        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus(), mustFinishMessage);

        // ---

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(expectedLog, ab);
    }
}
