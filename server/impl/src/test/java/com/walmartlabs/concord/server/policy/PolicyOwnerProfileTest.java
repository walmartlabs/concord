package com.walmartlabs.concord.server.policy;

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

import com.walmartlabs.concord.policyengine.EntityPolicy;
import com.walmartlabs.concord.policyengine.EntityRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyRules;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.security.UserProfile;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.identity.IdentityDao;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserInfo;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PolicyOwnerProfileTest {
    @AfterEach
    void cleanupSubject() {
        ThreadContext.remove();
    }

    @Test
    void entityCheckUsesSdkPrincipalAndEnforcesPolicy() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        SubjectPrincipal principal = new SubjectPrincipal(userId, "alice", null,
                new AuthenticationContext("test", null, null));
        DefaultSecurityManager securityManager = new DefaultSecurityManager();
        ThreadContext.bind(securityManager);
        ThreadContext.bind(new Subject.Builder(securityManager).authenticated(true)
                .principals(new SimplePrincipalCollection(principal, "test")).buildSubject());

        PolicyCache cache = mock(PolicyCache.class);
        PolicyEngine engine = mock(PolicyEngine.class);
        EntityRule deny = EntityRule.builder().entity("project").action("create").msg("denied").build();
        when(engine.getEntityPolicy()).thenReturn(new EntityPolicy(new PolicyRules<>(null, null, List.of(deny))));
        when(cache.get(orgId, projectId, userId)).thenReturn(engine);
        PolicyManager manager = new PolicyManager(cache, null, mock(UserManager.class));

        manager.checkEntity(orgId, projectId, EntityType.PROJECT, EntityAction.UPDATE, null, Map.of());
        ValidationErrorsException error = assertThrows(ValidationErrorsException.class,
                () -> manager.checkEntity(orgId, projectId, EntityType.PROJECT, EntityAction.CREATE, null, Map.of()));
        assertEquals("Action forbidden: denied", error.getMessage());
        verify(cache, times(2)).get(orgId, projectId, userId);
    }

    @Test
    void ownerProfileIsSelectedByUuidAndCannotOverwriteIdentity() {
        UUID id = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        UserEntry owner = mock(UserEntry.class);
        when(owner.getId()).thenReturn(id);
        when(owner.getType()).thenReturn(UserType.LDAP);
        UserManager users = mock(UserManager.class);
        when(users.getInfo(id)).thenReturn(UserInfo.builder().id(id).username("owner").email("owner@example.com")
                .attributes(Map.of("id", "spoof", "username", "spoof", "userType", "spoof", "profileProviderId", "spoof", "department", "A")).build());
        var identity = new ExternalIdentityKey("ldap", "", "subject");
        when(users.profileSelection(id)).thenReturn(new IdentityDao.ProfileSelection(
                new UserProfile("owner", null, null, null, Map.of()), "ldap", identityId, identity));

        var attrs = new PolicyManager(null, null, users).getOwnerAttrs(owner);
        assertEquals(id, attrs.get("id"));
        assertEquals("owner", attrs.get("username"));
        assertEquals("LDAP", attrs.get("userType"));
        assertEquals("ldap", attrs.get("profileProviderId"));
        assertEquals(identityId, attrs.get("profileIdentityId"));
        assertEquals(identity, attrs.get("identity"));
        assertEquals("A", attrs.get("department"));
        verify(users).getInfo(id);
    }
}
