package com.walmartlabs.concord.server.org;

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
import com.walmartlabs.concord.server.org.project.ProjectManager;
import com.walmartlabs.concord.server.org.inventory.InventoryOwner;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.apikey.ApiKeyManager;
import com.walmartlabs.concord.server.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.security.identity.IdentityDao;
import com.walmartlabs.concord.server.user.AccountSelector;
import org.jooq.DSLContext;
import org.jooq.Record;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerAccountSelectorTest {
    private final UUID userId = UUID.randomUUID();
    private final ExternalIdentityKey identity = new ExternalIdentityKey("oidc", "issuer", "subject");
    private final IdentityDao identities = mock(IdentityDao.class);
    private final UserManager users = mock(UserManager.class);

    private AccountSelector selector(boolean legacy) {
        return new AccountSelector(identities, ConfigFactory.parseString("security.legacy.enabled=" + legacy));
    }

    private ProjectManager projectManager(boolean legacy) {
        return new ProjectManager(null, null, null, null, null, null, null, null, null, null, users, selector(legacy));
    }

    @Test
    void resolvesOwnerIdentityAndRejectsMixedSelectors() {
        var account = mock(UserEntry.class);
        when(identities.find(identity)).thenReturn(Optional.of(new IdentityDao.IdentityRecord(UUID.randomUUID(), userId, identity, true)));
        when(users.get(userId)).thenReturn(Optional.of(account));
        var manager = projectManager(true);
        assertSame(account, manager.getOwner(EntityOwner.builder().identity(identity).build(), null));
        assertThrows(ValidationErrorsException.class, () -> manager.getOwner(EntityOwner.builder().id(userId).identity(identity).build(), null));
        assertThrows(ValidationErrorsException.class, () -> manager.getOwner(EntityOwner.builder().username("name").identity(identity).build(), null));
    }

    @Test
    void unknownOrRetiredIdentityIsNotAnOwner() {
        var manager = projectManager(true);
        when(identities.find(identity)).thenReturn(Optional.empty(),
                Optional.of(new IdentityDao.IdentityRecord(UUID.randomUUID(), userId, identity, false)));
        var owner = EntityOwner.builder().identity(identity).build();
        assertThrows(ConcordApplicationException.class, () -> manager.getOwner(owner, null));
        assertThrows(ConcordApplicationException.class, () -> manager.getOwner(owner, null));
    }

    @Test
    void requestBodiesDeserializeIdentity() throws Exception {
        var mapper = new ObjectMapper();
        String value = "{\"providerId\":\"oidc\",\"authority\":\"issuer\",\"subject\":\"subject\"}";
        assertEquals(identity, mapper.readValue("{\"identity\":" + value + "}", CreateApiKeyRequest.class).getIdentity());
        assertEquals(identity, mapper.readValue("{\"identity\":" + value + "}", EntityOwner.class).identity());
        assertEquals(identity, mapper.readValue("{\"identity\":" + value + "}", InventoryOwner.class).getIdentity());
    }

    @Test
    void uuidPrecedesLegacyFieldsOnlyWhenCompatibilityIsEnabled() {
        var account = mock(UserEntry.class);
        when(users.get(userId)).thenReturn(Optional.of(account));
        var owner = EntityOwner.builder().id(userId).username("legacy").userType(UserType.LDAP).build();
        assertSame(account, projectManager(true).getOwner(owner, null));
        assertThrows(ValidationErrorsException.class, () -> projectManager(false).getOwner(owner, null));
    }

    @Test
    void projectOwnerHonorsExplicitLegacyTypeAndKeepsLdapDefault() {
        var account = mock(UserEntry.class);
        when(users.get("alice", "example", UserType.LOCAL)).thenReturn(Optional.of(account));
        when(users.get("bob", null, UserType.LDAP)).thenReturn(Optional.of(account));
        var manager = projectManager(true);
        assertSame(account, manager.getOwner(EntityOwner.builder().username("alice")
                .userDomain("example").userType(UserType.LOCAL).build(), null));
        assertSame(account, manager.getOwner(EntityOwner.builder().username("bob").build(), null));
    }

    @Test
    void ownerProjectionIncludesProviderMetadataAndOmitsLegacyTypeWhenDisabled() {
        DSLContext db = mock(DSLContext.class);
        Record row = mock(Record.class);
        when(db.fetchOne(anyString(), (Object) any(), (Object) any())).thenReturn(row);
        when(row.get(0, String.class)).thenReturn("oidc");
        when(row.get(1, String[].class)).thenReturn(new String[]{"oidc", "ldap"});
        var owner = new OwnerProjection(selector(false)).owner(db, userId, "name", "domain", "Name", "LDAP");
        assertEquals("oidc", owner.profileProviderId());
        assertEquals(java.util.Set.of("oidc", "ldap"), owner.identityProviderIds());
        assertNull(owner.userType());
    }

    @Test
    void apiKeySelectorsRejectMixedAndDisabledLegacyFields() {
        var manager = new ApiKeyManager(mock(com.walmartlabs.concord.server.cfg.ApiKeyConfiguration.class), users,
                mock(com.walmartlabs.concord.server.security.apikey.ApiKeyDao.class),
                mock(com.walmartlabs.concord.server.audit.AuditLog.class), selector(true));
        var mixed = new CreateApiKeyRequest(userId, null, null, null, null, null, identity);
        assertThrows(ValidationErrorsException.class, () -> manager.create(mixed));
        assertThrows(ValidationErrorsException.class, () -> manager.createOrUpdate(mixed));
        var disabled = new ApiKeyManager(mock(com.walmartlabs.concord.server.cfg.ApiKeyConfiguration.class), users,
                mock(com.walmartlabs.concord.server.security.apikey.ApiKeyDao.class),
                mock(com.walmartlabs.concord.server.audit.AuditLog.class), selector(false));
        assertThrows(ValidationErrorsException.class, () -> disabled.create(new CreateApiKeyRequest(userId, "legacy", null, null, null, null)));
    }
}
