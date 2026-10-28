package com.walmartlabs.concord.server.role;

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

import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleGroupsResourceTest {
    private final RoleDao dao = mock(RoleDao.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final RoleGroupsResource resource = new RoleGroupsResource(dao, authorization, mock(AuditLog.class));

    @AfterEach
    void clearSubject() {
        ThreadContext.unbindSubject();
    }

    @Test
    void requiresAdminBeforeConsultingSourceOrMappings() {
        Subject subject = mock(Subject.class);
        ThreadContext.bind(subject);
        assertThrows(UnauthorizedException.class, () -> resource.list("reader", "ldap"));
        verifyNoInteractions(dao, authorization);
    }

    @Test
    void listIsScopedToRequestedSource() {
        Subject subject = mock(Subject.class);
        when(subject.hasRole(Roles.ADMIN)).thenReturn(true);
        ThreadContext.bind(subject);
        UUID id = UUID.randomUUID();
        when(authorization.isActiveSource("oidc")).thenReturn(true);
        when(dao.getId("reader")).thenReturn(id);
        when(dao.listGroups(id, "oidc")).thenReturn(List.of("staff"));
        assertEquals(List.of("staff"), resource.list("reader", "oidc"));
        verify(dao, never()).listGroups(id, "ldap");
        assertThrows(ValidationErrorsException.class, () -> resource.list("reader", "unknown"));
    }
}
