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

import com.walmartlabs.concord.server.process.form.FormAccessManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.GroupRef;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormAuthorizationTest {
    @AfterEach
    void cleanup() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
    }

    @Test
    void legacyLdapRuleUsesOnlyCurrentLdapSourceGroups() {
        UUID id = UUID.randomUUID();
        UserManager users = mock(UserManager.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        when(users.get(id)).thenReturn(Optional.of(new UserEntry(id, "alice", null, null, null, null, null, null, false, null, false)));
        SubjectPrincipal principal = new SubjectPrincipal(id, "old-name", null, new AuthenticationContext("test", null, null));
        DefaultSecurityManager manager = new DefaultSecurityManager();
        ThreadContext.bind(manager);
        ThreadContext.bind(new Subject.Builder(manager).authenticated(true)
                .principals(new SimplePrincipalCollection(principal, "test")).buildSubject());
        FormAccessManager forms = new FormAccessManager(mock(ProcessStateManager.class), users, authorization);
        Map<String, Serializable> rule = new HashMap<>();
        rule.put("ldap", new HashMap<>(Map.of("group", "operators")));
        when(authorization.getGroups(id)).thenReturn(Set.of(new GroupRef("other", "operators")),
                Set.of(new GroupRef("ldap", "CN=operators,OU=Groups")), Set.of());
        assertThrows(UnauthorizedException.class, () -> forms.assertFormAccess("approval", rule));
        assertDoesNotThrow(() -> forms.assertFormAccess("approval", rule));
        assertThrows(UnauthorizedException.class, () -> forms.assertFormAccess("approval", rule));
    }
}
