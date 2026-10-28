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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.identity.IdentityDao;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSelectorTest {
    private final IdentityDao identities = mock(IdentityDao.class);
    private final ExternalIdentityKey key = new ExternalIdentityKey("oidc", "issuer", "subject");

    @Test
    void explicitSelectorsConflictButUuidKeepsLegacyPrecedence() {
        AccountSelector selectors = new AccountSelector(identities, ConfigFactory.empty());
        UUID id = UUID.randomUUID();
        assertDoesNotThrow(() -> selectors.validate(id, null, true));
        assertThrows(ValidationErrorsException.class, () -> selectors.validate(id, key, false));
        assertThrows(ValidationErrorsException.class, () -> selectors.validate(null, key, true));
    }

    @Test
    void disabledCompatibilityRejectsEvenIgnoredLegacyFields() {
        AccountSelector selectors = new AccountSelector(identities, ConfigFactory.parseString("security.legacy.enabled=false"));
        assertThrows(ValidationErrorsException.class, () -> selectors.validate(UUID.randomUUID(), null, true));
        assertThrows(ValidationErrorsException.class, selectors::requireLegacy);
        assertDoesNotThrow(() -> selectors.validate(null, key, false));
    }

    @Test
    void creationRequestAcceptsIdentityWithoutLegacySelector() throws Exception {
        CreateUserRequest request = new ObjectMapper().readValue(
                "{\"identity\":{\"providerId\":\"oidc\",\"authority\":\"issuer\",\"subject\":\"subject\"},\"displayName\":\"Alice\"}",
                CreateUserRequest.class);
        assertEquals(key, request.getIdentity());
        assertNull(request.getUserId());
        assertEquals("Alice", request.getDisplayName());
    }

    @Test
    void onlyActiveOwnedIdentityResolves() {
        AccountSelector selectors = new AccountSelector(identities, ConfigFactory.empty());
        UUID userId = UUID.randomUUID();
        when(identities.find(key)).thenReturn(Optional.of(new IdentityDao.IdentityRecord(UUID.randomUUID(), userId, key, true)));
        assertEquals(userId, selectors.identityId(key));
        when(identities.find(key)).thenReturn(Optional.of(new IdentityDao.IdentityRecord(UUID.randomUUID(), userId, key, false)));
        assertThrows(ConcordApplicationException.class, () -> selectors.identityId(key));
        when(identities.find(key)).thenReturn(Optional.empty());
        assertThrows(ConcordApplicationException.class, () -> selectors.identityId(key));
    }
}
