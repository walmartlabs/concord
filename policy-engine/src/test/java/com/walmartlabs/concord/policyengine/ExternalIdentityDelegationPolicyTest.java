package com.walmartlabs.concord.policyengine;

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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExternalIdentityDelegationPolicyTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID REPOSITORY = UUID.randomUUID();
    private static final ExternalIdentityDelegationRule.Identity IDENTITY =
            new ExternalIdentityDelegationRule.Identity("provider", "authority", "subject");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void missingNullAndEmptyRulesDeny() {
        for (var raw : List.of(Map.of(), Map.of("externalIdentityDelegation", Map.of("allow", List.of())))) {
            var engine = new PolicyEngine(mapper.convertValue(raw, PolicyEngineRules.class));
            assertTrue(engine.getExternalIdentityDelegationPolicy().find(IDENTITY, ORG, PROJECT, REPOSITORY).isEmpty());
        }
        assertTrue(new ExternalIdentityDelegationPolicy(null).find(IDENTITY, ORG, PROJECT, REPOSITORY).isEmpty());
    }

    @Test
    void typedRuleRoundTripsWithoutDroppingEmptyAllowListOrResourceLists() throws Exception {
        var empty = mapper.convertValue(Map.of("externalIdentityDelegation", Map.of("allow", List.of())), PolicyEngineRules.class);
        var roundTrip = mapper.readValue(mapper.writeValueAsString(empty), PolicyEngineRules.class);
        assertNotNull(roundTrip.externalIdentityDelegationRule());
        assertTrue(roundTrip.externalIdentityDelegationRule().allow().isEmpty());
        var rules = PolicyEngineRules.builder()
                .externalIdentityDelegationRule(new ExternalIdentityDelegationRule(List.of(entry())))
                .build();
        assertEquals(rules.externalIdentityDelegationRule(),
                mapper.readValue(mapper.writeValueAsString(rules), PolicyEngineRules.class).externalIdentityDelegationRule());
        assertTrue(rules.customRule() == null || !rules.customRule().containsKey("externalIdentityDelegation"));
    }

    @Test
    void onlyExactIdentityAndIntegrationMatch() {
        var policy = new ExternalIdentityDelegationPolicy(new ExternalIdentityDelegationRule(List.of(entry())));
        assertEquals(entry(), policy.find(IDENTITY, ORG, PROJECT, REPOSITORY).orElseThrow());
        for (var identity : List.of(
                new ExternalIdentityDelegationRule.Identity("other", "authority", "subject"),
                new ExternalIdentityDelegationRule.Identity("provider", "other", "subject"),
                new ExternalIdentityDelegationRule.Identity("provider", "authority", "SUBJECT"))) {
            assertTrue(policy.find(identity, ORG, PROJECT, REPOSITORY).isEmpty());
        }
        assertTrue(policy.find(IDENTITY, UUID.randomUUID(), PROJECT, REPOSITORY).isEmpty());
        assertTrue(policy.find(IDENTITY, ORG, UUID.randomUUID(), REPOSITORY).isEmpty());
        assertTrue(policy.find(IDENTITY, ORG, PROJECT, UUID.randomUUID()).isEmpty());
    }

    @Test
    void unknownKeysFailEvenWithPermissiveMapper() {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertThrows(IllegalArgumentException.class, () -> mapper.convertValue(
                Map.of("allow", List.of(), "projects", List.of()), ExternalIdentityDelegationRule.class));
        assertThrows(IllegalArgumentException.class, () -> mapper.convertValue(
                Map.of("providerId", "provider", "authority", "authority", "subjectId", "subject", "extra", true),
                ExternalIdentityDelegationRule.Identity.class));
        assertThrows(IllegalArgumentException.class, () -> mapper.convertValue(
                Map.of("id", UUID.randomUUID(), "accessLevel", "READER", "extra", true),
                ExternalIdentityDelegationRule.Resource.class));
    }

    @Test
    void listsAreDefensiveAndNullListsDenyByErrorRatherThanAnAllowDefault() {
        var entries = new java.util.ArrayList<>(List.of(entry()));
        var rule = new ExternalIdentityDelegationRule(entries);
        entries.clear();
        assertEquals(1, rule.allow().size());
        assertThrows(UnsupportedOperationException.class, () -> rule.allow().clear());
        assertThrows(NullPointerException.class, () -> new ExternalIdentityDelegationRule(null));
        assertThrows(IllegalArgumentException.class, () -> mapper.convertValue(Map.of(), ExternalIdentityDelegationRule.class));
    }

    private ExternalIdentityDelegationRule.Delegation entry() {
        return new ExternalIdentityDelegationRule.Delegation("integration", IDENTITY, ORG, PROJECT, REPOSITORY,
                "READER", List.of(), List.of());
    }
}
