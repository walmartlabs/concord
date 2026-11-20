package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.TestObjectMapper;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PolicyExportProcessorTest {

    @TempDir
    Path workspace;

    @Test
    void exportsOnlyAgentRulesWithoutMutatingServerPolicyOrLosingCustomRules() throws Exception {
        var rules = TestObjectMapper.INSTANCE.readValue("""
                {
                  "processCfg": {"arguments": {"example": "value"}},
                  "customPluginRule": {"allow": ["example"]},
                  "externalIdentityDelegation": {
                    "allow": [{
                      "id": "source-integration",
                      "identity": {"providerId": "provider", "authority": "authority", "subjectId": "app"},
                      "orgId": "00000000-0000-0000-0000-000000000001",
                      "projectId": "00000000-0000-0000-0000-000000000002",
                      "repositoryId": "00000000-0000-0000-0000-000000000003",
                      "projectAccessLevel": "READER",
                      "secrets": [],
                      "jsonStores": []
                    }]
                  }
                }
                """, PolicyEngineRules.class);
        assertExport(rules);
    }

    @Test
    void omitsExplicitEmptyDelegationRule() throws Exception {
        var rules = TestObjectMapper.INSTANCE.readValue("""
                {"externalIdentityDelegation": {"allow": []}}
                """, PolicyEngineRules.class);
        assertExport(rules);
    }

    @Test
    void policiesWithoutDelegationKeepTheirExportedContent() throws Exception {
        var rules = TestObjectMapper.INSTANCE.readValue("""
                {"processCfg": {"arguments": {"example": "value"}}, "customPluginRule": {"enabled": true}}
                """, PolicyEngineRules.class);
        assertExport(rules);
    }

    @Test
    void absentPolicyDoesNotCreateAnExport() {
        var manager = mock(PolicyManager.class);
        var processor = new PolicyExportProcessor(TestObjectMapper.INSTANCE, manager, mock(ProcessLogManager.class));
        var payload = new Payload(ProcessKey.random()).putHeader(Payload.WORKSPACE_DIR, workspace);
        assertSame(payload, processor.process(new Chain(), payload));
        assertFalse(Files.exists(policyFile()));
    }

    private void assertExport(PolicyEngineRules rules) throws Exception {
        var manager = mock(PolicyManager.class);
        var processor = new PolicyExportProcessor(TestObjectMapper.INSTANCE, manager, mock(ProcessLogManager.class));
        var policy = new PolicyEngine("effective-policy", rules);
        var orgId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var payload = new Payload(ProcessKey.random())
                .putHeader(Payload.WORKSPACE_DIR, workspace)
                .putHeader(Payload.ORGANIZATION_ID, orgId)
                .putHeader(Payload.PROJECT_ID, projectId)
                .putHeader(Payload.INITIATOR_ID, userId);
        when(manager.get(orgId, projectId, userId)).thenReturn(policy);
        var original = TestObjectMapper.INSTANCE.valueToTree(rules);
        var result = processor.process(new Chain(), payload);
        var exported = TestObjectMapper.INSTANCE.readTree(policyFile().toFile());
        var expected = original.deepCopy();
        assertTrue(expected.isObject());
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected).remove("externalIdentityDelegation");
        assertEquals(expected, exported);
        assertFalse(exported.has("externalIdentityDelegation"));
        assertSame(policy, result.getHeader(Payload.POLICY));
        assertSame(rules, policy.getRules());
        assertEquals(original, TestObjectMapper.INSTANCE.valueToTree(policy.getRules()));
    }

    private Path policyFile() {
        return workspace.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME).resolve(Constants.Files.POLICY_FILE_NAME);
    }
}
