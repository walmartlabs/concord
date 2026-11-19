package com.walmartlabs.concord.server.policy;

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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.policyengine.EntityRule;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.server.TestObjectMapper;
import com.walmartlabs.concord.server.cfg.PolicyCacheConfiguration;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PolicyCacheTest {

    @Test
    public void delegationUsesExistingPolicyLinksAndSelection() {
        var dao = mock(PolicyCache.Dao.class);
        var cache = new PolicyCache(TestObjectMapper.INSTANCE, new PolicyCacheConfiguration(), dao);
        var orgId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var defaultId = UUID.randomUUID();
        var orgPolicyId = UUID.randomUUID();
        var projectPolicyId = UUID.randomUUID();
        when(dao.listPolicies()).thenReturn(List.of(
                PolicyCache.PolicyRules.builder().id(defaultId).name("default").rules(delegationRules()).build(),
                PolicyCache.PolicyRules.builder().id(orgPolicyId).name("org").rules(Map.of()).build(),
                PolicyCache.PolicyRules.builder().id(projectPolicyId).name("project").rules(delegationRules()).build()));
        when(dao.listLinks()).thenReturn(List.of(
                ImmutablePolicyLink.builder().policyId(defaultId).build(),
                ImmutablePolicyLink.builder().policyId(orgPolicyId).orgId(orgId).build(),
                ImmutablePolicyLink.builder().policyId(projectPolicyId).projectId(projectId).build()));
        cache.refresh();

        assertEquals(List.of("project"), cache.get(orgId, projectId, userId).policyNames());
        assertTrue(cache.get(orgId, projectId, userId).getRules().externalIdentityDelegationRule().allow().isEmpty());
        assertEquals(List.of("org"), cache.get(orgId, UUID.randomUUID(), userId).policyNames());
        assertNull(cache.get(orgId, UUID.randomUUID(), userId).getRules().externalIdentityDelegationRule());
        assertEquals(List.of("default"), cache.get(UUID.randomUUID(), UUID.randomUUID(), userId).policyNames());
    }

    @Test
    public void delegationListInheritsReplacesAndClearsUsingNormalParentMerge() {
        var cache = new PolicyCache(TestObjectMapper.INSTANCE, new PolicyCacheConfiguration(), mock(PolicyCache.Dao.class));
        var parentId = UUID.randomUUID();
        var inheritedId = UUID.randomUUID();
        var deniedId = UUID.randomUUID();
        var replacedId = UUID.randomUUID();
        var entry = delegationEntry("parent");
        var merged = cache.mergePolicies(List.of(
                PolicyCache.PolicyRules.builder().id(parentId).name("parent")
                        .rules(Map.of("externalIdentityDelegation", Map.of("allow", List.of(entry)))).build(),
                PolicyCache.PolicyRules.builder().id(inheritedId).parentId(parentId).name("inherited").rules(Map.of()).build(),
                PolicyCache.PolicyRules.builder().id(deniedId).parentId(parentId).name("denied").rules(delegationRules()).build(),
                PolicyCache.PolicyRules.builder().id(replacedId).parentId(parentId).name("replaced")
                        .rules(Map.of("externalIdentityDelegation", Map.of("allow", List.of(delegationEntry("child"))))).build()));
        assertEquals("parent", merged.get(inheritedId).rules().externalIdentityDelegationRule().allow().get(0).id());
        assertTrue(merged.get(deniedId).rules().externalIdentityDelegationRule().allow().isEmpty());
        assertEquals(1, merged.get(replacedId).rules().externalIdentityDelegationRule().allow().size());
        assertEquals("child", merged.get(replacedId).rules().externalIdentityDelegationRule().allow().get(0).id());
    }

    private static Map<String, Object> delegationRules() {
        return Map.of("externalIdentityDelegation", Map.of("allow", List.of()));
    }

    private static Map<String, Object> delegationEntry(String id) {
        return Map.of("id", id,
                "identity", Map.of("providerId", "provider", "authority", "authority", "subjectId", "subject"),
                "orgId", UUID.randomUUID().toString(), "projectId", UUID.randomUUID().toString(),
                "repositoryId", UUID.randomUUID().toString(),
                "projectAccessLevel", "READER", "secrets", List.of(), "jsonStores", List.of());
    }

    @Test
    public void allowNullValues() {
        PolicyCache.Dao dao = mock(PolicyCache.Dao.class);
        PolicyCache pc = new PolicyCache(TestObjectMapper.INSTANCE, new PolicyCacheConfiguration(), dao);

        // ---
        Map<String, Object> ruleParams = new HashMap<>();
        ruleParams.put("nullValue", null);
        ruleParams.put("nullValue2", null);
        Map<String, Object> conditions = Collections.singletonMap("entity", ImmutableMap.of("params", ruleParams));

        Map<String, Object> denyEntityRule = new HashMap<>();
        denyEntityRule.put("msg", "test message");
        denyEntityRule.put("action", "create");
        denyEntityRule.put("entity", "trigger");
        denyEntityRule.put("conditions", conditions);

        Map<String, Object> rules = new HashMap<>();
        rules.put("entity", Collections.singletonMap("deny", Collections.singletonList(denyEntityRule)));

        List<PolicyCache.PolicyRules> policies = Collections.singletonList(
                PolicyCache.PolicyRules.builder()
                        .id(UUID.randomUUID())
                        .name("test")
                        .rules(rules)
                        .build());

        // ---
        Map<UUID, PolicyCache.Policy> merged = pc.mergePolicies(policies);
        assertEquals(1, merged.size());

        PolicyEngineRules actualRules = merged.values().iterator().next().rules();
        assertEquals(1, actualRules.entityRules().getDeny().size());

        EntityRule actualRule = actualRules.entityRules().getDeny().get(0);
        assertEquals(conditions, actualRule.conditions());
    }
}
