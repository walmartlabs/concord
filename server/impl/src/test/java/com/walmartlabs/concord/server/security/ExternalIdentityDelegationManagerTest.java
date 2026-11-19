package com.walmartlabs.concord.server.security;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import com.walmartlabs.concord.server.user.ExternalIdentityDao;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalIdentityDelegationManagerTest {

    private static final ExternalIdentityRef IDENTITY = new ExternalIdentityRef("provider", "authority", "subject");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID REPOSITORY = UUID.randomUUID();
    private static final UUID SECRET = UUID.randomUUID();
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final PolicyManager policyManager = mock(PolicyManager.class);
    private final ExternalIdentityDao identityDao = mock(ExternalIdentityDao.class);

    @Test
    void noPoliciesDenyEveryIntegration() {
        var manager = manager(List.of());
        assertThrows(UnauthorizedException.class, () -> manager.requirePolicy(IDENTITY, ORG, PROJECT, REPOSITORY));
    }

    @Test
    void exactBindingIsRequiredForEveryIdentityAndIntegrationComponent() {
        var manager = manager(List.of(entry()));
        assertEquals("app-project", manager.requirePolicy(IDENTITY, ORG, PROJECT, REPOSITORY).id());
        for (var identity : List.of(
                new ExternalIdentityRef("other", "authority", "subject"),
                new ExternalIdentityRef("provider", "other", "subject"),
                new ExternalIdentityRef("provider", "authority", "other"),
                new ExternalIdentityRef("provider", "authority", "SUBJECT"))) {
            assertThrows(UnauthorizedException.class, () -> manager.requirePolicy(identity, ORG, PROJECT, REPOSITORY));
        }
        assertThrows(UnauthorizedException.class, () -> manager.requirePolicy(IDENTITY, UUID.randomUUID(), PROJECT, REPOSITORY));
        assertThrows(UnauthorizedException.class, () -> manager.requirePolicy(IDENTITY, ORG, UUID.randomUUID(), REPOSITORY));
        assertThrows(UnauthorizedException.class, () -> manager.requirePolicy(IDENTITY, ORG, PROJECT, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> manager.requirePolicy(null, ORG, PROJECT, REPOSITORY));
        assertThrows(NullPointerException.class, () -> manager.requirePolicy(IDENTITY, null, PROJECT, REPOSITORY));
        assertThrows(NullPointerException.class, () -> manager.requirePolicy(IDENTITY, ORG, null, REPOSITORY));
        assertThrows(NullPointerException.class, () -> manager.requirePolicy(IDENTITY, ORG, PROJECT, null));
    }

    @Test
    void differentIntegrationsDoNotUnionResourceGrants() {
        var first = entry();
        first.put("secrets", List.of(resource(SECRET, "READER")));
        var otherProject = UUID.randomUUID();
        var otherRepository = UUID.randomUUID();
        var second = entry();
        second.put("id", "other-project");
        second.put("projectId", otherProject.toString());
        second.put("repositoryId", otherRepository.toString());
        second.put("jsonStores", List.of(resource(STORE, "WRITER")));
        var manager = manager(List.of(first, second));

        var firstPolicy = manager.requirePolicy(IDENTITY, ORG, PROJECT, REPOSITORY);
        firstPolicy.assertSecretAccess(SECRET, ResourceAccessLevel.READER);
        assertThrows(UnauthorizedException.class, () -> firstPolicy.assertJsonStoreAccess(STORE, ResourceAccessLevel.READER));
        assertThrows(UnauthorizedException.class, () -> firstPolicy.assertProjectAccess(otherProject, ResourceAccessLevel.READER));
        var secondPolicy = manager.requirePolicy(IDENTITY, ORG, otherProject, otherRepository);
        secondPolicy.assertJsonStoreAccess(STORE, ResourceAccessLevel.WRITER);
        assertThrows(UnauthorizedException.class, () -> secondPolicy.assertSecretAccess(SECRET, ResourceAccessLevel.READER));
        assertThrows(UnauthorizedException.class, () -> secondPolicy.assertProjectAccess(PROJECT, ResourceAccessLevel.READER));
    }

    @Test
    void projectGrantDoesNotImplicitlyGrantSecretsOrStores() {
        var policy = require(entry());
        policy.assertProjectAccess(PROJECT, ResourceAccessLevel.READER);
        assertThrows(UnauthorizedException.class, () -> policy.assertSecretAccess(SECRET, ResourceAccessLevel.READER));
        assertThrows(UnauthorizedException.class, () -> policy.assertJsonStoreAccess(STORE, ResourceAccessLevel.READER));
    }

    @Test
    void everyAccessLevelCapMatchesConcordOrderingAndNeverAnotherProject() {
        for (var cap : ResourceAccessLevel.values()) {
            var entry = entry();
            entry.put("projectAccessLevel", cap.name());
            entry.put("secrets", List.of(resource(SECRET, cap.name())));
            entry.put("jsonStores", List.of(resource(STORE, cap.name())));
            var policy = require(entry);
            for (var requested : ResourceAccessLevel.values()) {
                if (Arrays.asList(ResourceAccessLevel.atLeast(requested)).contains(cap)) {
                    assertDoesNotThrow(() -> policy.assertProjectAccess(PROJECT, requested));
                    assertDoesNotThrow(() -> policy.assertSecretAccess(SECRET, requested));
                    assertDoesNotThrow(() -> policy.assertJsonStoreAccess(STORE, requested));
                } else {
                    assertThrows(UnauthorizedException.class, () -> policy.assertProjectAccess(PROJECT, requested));
                    assertThrows(UnauthorizedException.class, () -> policy.assertSecretAccess(SECRET, requested));
                    assertThrows(UnauthorizedException.class, () -> policy.assertJsonStoreAccess(STORE, requested));
                }
                assertThrows(UnauthorizedException.class, () -> policy.assertProjectAccess(UUID.randomUUID(), requested));
            }
        }
    }

    @Test
    void resourceKindsAndCapsAreIndependentEvenForSameUuid() {
        var entry = entry();
        entry.put("secrets", List.of(resource(SECRET, "READER")));
        entry.put("jsonStores", List.of(resource(SECRET, "WRITER")));
        var policy = require(entry);
        policy.assertSecretAccess(SECRET, ResourceAccessLevel.READER);
        assertThrows(UnauthorizedException.class, () -> policy.assertSecretAccess(SECRET, ResourceAccessLevel.WRITER));
        policy.assertJsonStoreAccess(SECRET, ResourceAccessLevel.WRITER);
        assertThrows(UnauthorizedException.class, () -> policy.assertJsonStoreAccess(SECRET, ResourceAccessLevel.OWNER));
    }

    @Test
    void duplicatePolicyIdsAndDuplicateBindingsFailInsteadOfSelectingByOrder() {
        var first = entry();
        var sameId = entry();
        sameId.put("repositoryId", UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class, () -> manager(List.of(first, sameId)));
        var sameBinding = entry();
        sameBinding.put("id", "same-binding");
        assertThrows(IllegalArgumentException.class, () -> manager(List.of(first, sameBinding)));
    }

    @Test
    void duplicateResourceIdsFailEvenIfCapsAgree() {
        for (var key : List.of("secrets", "jsonStores")) {
            for (var duplicateCap : List.of("READER", "OWNER")) {
                var entry = entry();
                entry.put(key, List.of(resource(SECRET, "READER"), resource(SECRET, duplicateCap)));
                assertThrows(IllegalArgumentException.class, () -> require(entry));
            }
        }
    }

    @Test
    void unknownKeysAtEveryLevelFailRatherThanIgnoringRestrictions() {
        for (var key : List.of("projects", "teamIds", "secret", "extra")) {
            var entry = entry();
            entry.put(key, List.of());
            assertThrows(IllegalArgumentException.class, () -> require(entry));
        }
        var entry = entry();
        var identity = new HashMap<>(Map.of("providerId", "provider", "authority", "authority", "subjectId", "subject", "extra", "value"));
        entry.put("identity", identity);
        assertThrows(IllegalArgumentException.class, () -> require(entry));
        for (var key : List.of("secrets", "jsonStores")) {
            var invalid = entry();
            var resource = resource(SECRET, "READER");
            resource.put("projectId", PROJECT.toString());
            invalid.put(key, List.of(resource));
            assertThrows(IllegalArgumentException.class, () -> require(invalid));
        }
    }

    @Test
    void requiredFieldsCannotBeOmittedOrNull() {
        for (var key : entry().keySet()) {
            var missing = entry();
            missing.remove(key);
            assertThrows(IllegalArgumentException.class, () -> require(missing));
            var nullValue = entry();
            nullValue.put(key, null);
            assertThrows(IllegalArgumentException.class, () -> require(nullValue));
        }
        for (var key : List.of("providerId", "authority", "subjectId")) {
            var entry = entry();
            var identity = new HashMap<>(Map.of("providerId", "provider", "authority", "authority", "subjectId", "subject"));
            identity.remove(key);
            entry.put("identity", identity);
            assertThrows(IllegalArgumentException.class, () -> require(entry));
        }
    }

    @Test
    void invalidIdsLevelsWildcardsAndIdentityComponentsFail() {
        for (var id : List.of("", " ", "a".repeat(129))) {
            var entry = entry();
            entry.put("id", id);
            assertThrows(IllegalArgumentException.class, () -> require(entry));
        }
        for (var key : List.of("orgId", "projectId", "repositoryId")) {
            for (var id : List.of("*", "project-name")) {
                var entry = entry();
                entry.put(key, id);
                assertThrows(IllegalArgumentException.class, () -> require(entry));
            }
        }
        for (var level : List.of("*", "reader", "ADMIN", "")) {
            var entry = entry();
            entry.put("projectAccessLevel", level);
            assertThrows(IllegalArgumentException.class, () -> require(entry));
            for (var key : List.of("secrets", "jsonStores")) {
                var invalid = entry();
                invalid.put(key, List.of(resource(SECRET, level)));
                assertThrows(IllegalArgumentException.class, () -> require(invalid));
            }
        }
        for (var key : List.of("providerId", "authority", "subjectId")) {
            for (var invalid : List.of(" ", "a".repeat(key.equals("providerId") ? 129 : 513))) {
                var entry = entry();
                var identity = new HashMap<>(Map.of("providerId", "provider", "authority", "authority", "subjectId", "subject"));
                identity.put(key, invalid);
                entry.put("identity", identity);
                assertThrows(IllegalArgumentException.class, () -> require(entry));
            }
        }
    }

    @Test
    void policiesAndResourceMapsAreImmutable() {
        var secrets = new HashMap<UUID, ResourceAccessLevel>();
        secrets.put(SECRET, ResourceAccessLevel.READER);
        var binding = new ExternalIdentityDelegationPolicy.Binding(IDENTITY, ORG, PROJECT, REPOSITORY);
        var policy = new ExternalIdentityDelegationPolicy("immutable", binding, ResourceAccessLevel.READER, secrets, Map.of());
        secrets.put(SECRET, ResourceAccessLevel.OWNER);
        assertThrows(UnauthorizedException.class, () -> policy.assertSecretAccess(SECRET, ResourceAccessLevel.OWNER));
        assertThrows(UnsupportedOperationException.class, () -> policy.secrets().clear());
        assertThrows(UnsupportedOperationException.class, () -> policy.jsonStores().put(STORE, ResourceAccessLevel.OWNER));

        var entry = entry();
        var entries = new ArrayList<>(List.of(entry));
        var manager = manager(entries);
        entries.clear();
        entry.put("projectAccessLevel", "OWNER");
        var loaded = manager.requirePolicy(IDENTITY, ORG, PROJECT, REPOSITORY);
        assertThrows(UnauthorizedException.class, () -> loaded.assertProjectAccess(PROJECT, ResourceAccessLevel.OWNER));
    }

    @Test
    void nullAccessRequestsAndInvalidPolicyConstructionFailExplicitly() {
        var policy = require(entry());
        assertThrows(NullPointerException.class, () -> policy.assertProjectAccess(null, ResourceAccessLevel.READER));
        assertThrows(NullPointerException.class, () -> policy.assertProjectAccess(PROJECT, null));
        assertThrows(NullPointerException.class, () -> policy.assertSecretAccess(null, ResourceAccessLevel.READER));
        assertThrows(NullPointerException.class, () -> policy.assertSecretAccess(SECRET, null));
        assertThrows(NullPointerException.class, () -> policy.assertJsonStoreAccess(null, ResourceAccessLevel.READER));
        assertThrows(NullPointerException.class, () -> policy.assertJsonStoreAccess(STORE, null));
        assertThrows(NullPointerException.class, () -> new ExternalIdentityDelegationPolicy(null, policy.binding(),
                ResourceAccessLevel.READER, Map.of(), Map.of()));
        assertThrows(NullPointerException.class, () -> new ExternalIdentityDelegationPolicy("id", null,
                ResourceAccessLevel.READER, Map.of(), Map.of()));
        assertThrows(NullPointerException.class, () -> new ExternalIdentityDelegationPolicy("id", policy.binding(),
                null, Map.of(), Map.of()));
        assertThrows(NullPointerException.class, () -> new ExternalIdentityDelegationPolicy("id", policy.binding(),
                ResourceAccessLevel.READER, null, Map.of()));
        assertThrows(NullPointerException.class, () -> new ExternalIdentityDelegationPolicy("id", policy.binding(),
                ResourceAccessLevel.READER, Map.of(), null));
    }

    @Test
    void noEffectivePolicyOrNoDelegationRuleDenies() {
        when(identityDao.getUserId(IDENTITY)).thenReturn(Optional.of(USER));
        var manager = new ExternalIdentityDelegationManager(policyManager, identityDao);
        assertThrows(UnauthorizedException.class, () -> manager.requirePolicy(IDENTITY, ORG, PROJECT, REPOSITORY));
        when(policyManager.get(ORG, PROJECT, USER)).thenReturn(new PolicyEngine(PolicyEngineRules.builder().build()));
        assertThrows(UnauthorizedException.class, () -> manager.requirePolicy(IDENTITY, ORG, PROJECT, REPOSITORY));
    }

    @Test
    void authoritativeBindingSelectsEffectivePolicyAndChangesAreNotCachedLocally() {
        var manager = manager(List.of(entry()));
        assertEquals("app-project", manager.requirePolicy(IDENTITY, ORG, PROJECT, REPOSITORY).id());
        verify(policyManager).get(ORG, PROJECT, USER);
        when(policyManager.get(ORG, PROJECT, USER)).thenReturn(new PolicyEngine(PolicyEngineRules.builder().build()));
        assertThrows(UnauthorizedException.class, () -> manager.requirePolicy(IDENTITY, ORG, PROJECT, REPOSITORY));
        verify(policyManager, times(2)).get(ORG, PROJECT, USER);
        when(identityDao.getUserId(IDENTITY)).thenReturn(Optional.empty());
        clearInvocations(policyManager);
        assertThrows(UnauthorizedException.class, () -> manager.requirePolicy(IDENTITY, ORG, PROJECT, REPOSITORY));
        verifyNoInteractions(policyManager);
    }

    private ExternalIdentityDelegationPolicy require(Map<String, Object> entry) {
        return manager(List.of(entry)).requirePolicy(IDENTITY, ORG, PROJECT, REPOSITORY);
    }

    private ExternalIdentityDelegationManager manager(List<Map<String, Object>> entries) {
        var rules = new ObjectMapper().convertValue(
                Map.of("externalIdentityDelegation", Map.of("allow", entries)), PolicyEngineRules.class);
        when(identityDao.getUserId(any())).thenReturn(Optional.of(USER));
        when(policyManager.get(any(), any(), eq(USER))).thenReturn(new PolicyEngine(rules));
        return new ExternalIdentityDelegationManager(policyManager, identityDao);
    }

    private Map<String, Object> entry() {
        var result = new HashMap<String, Object>();
        result.put("id", "app-project");
        result.put("identity", Map.of("providerId", "provider", "authority", "authority", "subjectId", "subject"));
        result.put("orgId", ORG.toString());
        result.put("projectId", PROJECT.toString());
        result.put("repositoryId", REPOSITORY.toString());
        result.put("projectAccessLevel", "READER");
        result.put("secrets", List.of());
        result.put("jsonStores", List.of());
        return result;
    }

    private Map<String, Object> resource(UUID id, String accessLevel) {
        var result = new HashMap<String, Object>();
        result.put("id", id.toString());
        result.put("accessLevel", accessLevel);
        return result;
    }
}
