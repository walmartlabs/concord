package com.walmartlabs.concord.server.user;

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

import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.AuthorizationSubjectNotFoundException;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import com.walmartlabs.concord.server.security.identity.IdentityDao;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAuthorizationResourceTest {
    private final AuthorizationService service = mock(AuthorizationService.class);
    private final UserResourceV2 resource = new UserResourceV2(mock(UserDao.class), mock(IdentityDao.class), service, mock(AccountSelector.class));
    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void clearSubject() { ThreadContext.unbindSubject(); }

    @Test
    void syncIsAdminOnly() {
        ThreadContext.bind(mock(Subject.class));
        assertThrows(UnauthorizedException.class, () -> resource.syncAuthorization(userId, "ldap"));
        verifyNoInteractions(service);
    }

    @Test
    void syncDistinguishesMissingBindingMissingSubjectAndOutage() {
        Subject subject = mock(Subject.class);
        when(subject.hasRole(Roles.ADMIN)).thenReturn(true);
        ThreadContext.bind(subject);
        doThrow(new NoSuchElementException()).doThrow(new AuthorizationSubjectNotFoundException("missing"))
                .doThrow(new AuthorizationUnavailableException("offline")).when(service).refresh(userId, "ldap", true);
        assertEquals(404, assertThrows(ConcordApplicationException.class, () -> resource.syncAuthorization(userId, "ldap")).getResponse().getStatus());
        assertEquals(403, assertThrows(ConcordApplicationException.class, () -> resource.syncAuthorization(userId, "ldap")).getResponse().getStatus());
        assertEquals(503, assertThrows(ConcordApplicationException.class, () -> resource.syncAuthorization(userId, "ldap")).getResponse().getStatus());
    }
}
