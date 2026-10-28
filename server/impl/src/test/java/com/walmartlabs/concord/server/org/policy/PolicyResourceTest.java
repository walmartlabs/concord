package com.walmartlabs.concord.server.org.policy;

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
import com.typesafe.config.ConfigFactory;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.identity.IdentityDao;
import com.walmartlabs.concord.server.user.AccountSelector;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PolicyResourceTest {
    private final PolicyManager policies = mock(PolicyManager.class);
    private final UserManager users = mock(UserManager.class);
    private final IdentityDao identities = mock(IdentityDao.class);
    private final AuditLog audit = mock(AuditLog.class, RETURNS_DEEP_STUBS);
    private final UUID policyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private PolicyResource resource(boolean legacy) {
        return new PolicyResource(mock(OrganizationManager.class), mock(OrganizationDao.class),
                mock(ProjectDao.class), users, policies, audit,
                new AccountSelector(identities, ConfigFactory.parseString("security.legacy.enabled=" + legacy)));
    }

    private void admin() {
        Subject subject = mock(Subject.class);
        when(subject.hasRole(Roles.ADMIN)).thenReturn(true);
        ThreadContext.bind(subject);
        when(policies.getId("policy")).thenReturn(policyId);
        when(users.get(userId)).thenReturn(Optional.of(mock(UserEntry.class)));
    }

    @AfterEach
    void clearSubject() {
        ThreadContext.unbindSubject();
    }

    @Test
    void explicitIdentityAndUuidLinkToSameAccount() throws Exception {
        admin();
        ExternalIdentityKey key = new ExternalIdentityKey("oidc", "issuer", "Subject");
        when(identities.find(key)).thenReturn(Optional.of(new IdentityDao.IdentityRecord(UUID.randomUUID(), userId, key, true)));
        PolicyLinkEntry body = new ObjectMapper().readValue("{\"identity\":{\"providerId\":\"oidc\",\"authority\":\"issuer\",\"subject\":\"Subject\"}}", PolicyLinkEntry.class);
        resource(false).link("policy", body);
        resource(false).link("policy", new PolicyLinkEntry(null, null, null, null, userId, null));
        verify(policies, times(2)).link(policyId, null, null, userId);
        verify(users, never()).currentLegacyType();
    }

    @Test
    void selectorConflictsAndDisabledLegacyFieldsAreRejected() {
        admin();
        ExternalIdentityKey key = new ExternalIdentityKey("oidc", "issuer", "subject");
        assertThrows(ValidationErrorsException.class, () -> resource(true).link("policy", new PolicyLinkEntry(null, null, "name", null, null, key)));
        assertThrows(ValidationErrorsException.class, () -> resource(true).link("policy", new PolicyLinkEntry(null, null, null, null, userId, key)));
        assertThrows(ValidationErrorsException.class, () -> resource(false).link("policy", new PolicyLinkEntry(null, null, "name", null, userId, null)));
        assertThrows(ValidationErrorsException.class, () -> resource(false).list(null, null, null, "domain", null));
        assertThrows(ValidationErrorsException.class, () -> resource(false).unlink("policy", null, null, null, null, UserType.LOCAL));
        verify(policies, never()).link(any(), any(), any(), any());
    }

    @Test
    void uuidTakesPrecedenceOverLegacyFieldsWhenEnabled() {
        admin();
        resource(true).link("policy", new PolicyLinkEntry(null, null, "old", "domain", userId, null));
        verify(policies).link(policyId, null, null, userId);
        verify(users, never()).getId(any(), any(), any());
    }

    @Test
    void legacyNameUsesCallerTypeAndMissingIdentityIsNotLinked() {
        admin();
        when(users.currentLegacyType()).thenReturn(UserType.LOCAL);
        when(users.getId("old", "domain", UserType.LOCAL)).thenReturn(Optional.of(userId));
        resource(true).link("policy", new PolicyLinkEntry(null, null, "old", "domain"));
        verify(policies).link(policyId, null, null, userId);
        ExternalIdentityKey missing = new ExternalIdentityKey("oidc", "issuer", "missing");
        when(identities.find(missing)).thenReturn(Optional.empty());
        assertThrows(com.walmartlabs.concord.server.sdk.ConcordApplicationException.class,
                () -> resource(false).link("policy", new PolicyLinkEntry(null, null, null, null, null, missing)));
        verify(policies, times(1)).link(any(), any(), any(), any());
    }

    @Test
    void newOperationsAreAdminOnlyAndUseUserLevelOperations() {
        ThreadContext.bind(mock(Subject.class));
        assertThrows(UnauthorizedException.class, () -> resource(false).listPoliciesByUserId(userId));
        assertThrows(UnauthorizedException.class, () -> resource(false).unlinkPolicyFromUser("policy", userId));
        verifyNoInteractions(policies);
        admin();
        when(policies.listByUser(userId)).thenReturn(List.of());
        assertEquals(List.of(), resource(false).listPoliciesByUserId(userId));
        resource(false).unlinkPolicyFromUser("policy", userId);
        verify(policies).listByUser(userId);
        verify(policies).unlinkUser(policyId, userId);
        verify(policies, never()).getLinked(any(), any(), any());
        verify(policies, never()).unlink(any(), any(), any(), any());
    }
}
