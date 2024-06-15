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
import static org.mockito.Mockito.mock;

public class PolicyCacheTest {

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
