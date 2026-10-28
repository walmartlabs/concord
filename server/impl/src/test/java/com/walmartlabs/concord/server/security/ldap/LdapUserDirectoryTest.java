package com.walmartlabs.concord.server.security.ldap;

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

import com.walmartlabs.concord.server.sdk.security.DirectoryAmbiguousException;
import com.walmartlabs.concord.server.sdk.security.DirectoryUnavailableException;
import com.walmartlabs.concord.server.sdk.security.DirectoryQuery;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import org.junit.jupiter.api.Test;

import javax.naming.CommunicationException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LdapUserDirectoryTest {
    @Test
    void canonicalIdentityIsCompactAndLocaleIndependent() {
        ExternalIdentityKey key = LdapUserDirectory.key("İUSER", null);
        assertEquals("[\"i̇user\",null]", key.subject());
        LdapUserDirectory directory = new LdapUserDirectory(mock(LdapManager.class));
        assertTrue(directory.acceptsIdentity(key));
        assertFalse(directory.acceptsIdentity(new ExternalIdentityKey("ldap", "other", key.subject())));
        assertFalse(directory.acceptsIdentity(new ExternalIdentityKey("ldap", "", "[\"USER\",null]")));
        assertFalse(directory.acceptsIdentity(new ExternalIdentityKey("ldap", "", "[\"user\"]")));
    }

    @Test
    void lookupsUseExistingLdapSearchesAndDoNotExposeCredentials() throws Exception {
        LdapManager manager = mock(LdapManager.class);
        LdapPrincipal principal = new LdapPrincipal("Alice", "Example", "cn=alice,dc=example", null,
                "Alice", "alice@example", Set.of("group"), Map.of("userPassword", "secret"));
        when(manager.getPrincipal("alice", "example")).thenReturn(principal);
        when(manager.getPrincipalByMail("alice@example")).thenReturn(principal);
        when(manager.getPrincipalByDn("cn=alice,dc=example")).thenReturn(principal);
        LdapUserDirectory directory = new LdapUserDirectory(manager);
        var byName = directory.find(new DirectoryQuery("username", "alice", "example")).orElseThrow();
        assertEquals(LdapUserDirectory.key("alice", "example"), byName.identity());
        assertEquals(Map.of(), byName.profile().attributes());
        assertEquals(byName, directory.find(new DirectoryQuery("email", "alice@example", null)).orElseThrow());
        assertEquals(byName, directory.find(new DirectoryQuery("distinguishedName", "cn=alice,dc=example", null)).orElseThrow());
        assertEquals(byName.profile(), directory.get(byName.identity()).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> directory.find(new DirectoryQuery("groups", "x", null)));
    }

    @Test
    void distinguishesMissingAmbiguousAndUnavailable() throws Exception {
        LdapManager manager = mock(LdapManager.class);
        when(manager.getPrincipal("missing", null)).thenReturn(null);
        when(manager.getPrincipal("ambiguous", null)).thenThrow(new RuntimeException("LDAP error, non unique result found"));
        when(manager.getPrincipal("offline", null)).thenThrow(new CommunicationException("offline"));
        LdapUserDirectory directory = new LdapUserDirectory(manager);
        assertTrue(directory.find(new DirectoryQuery("username", "missing", null)).isEmpty());
        assertThrows(DirectoryAmbiguousException.class, () -> directory.find(new DirectoryQuery("username", "ambiguous", null)));
        assertThrows(DirectoryUnavailableException.class, () -> directory.find(new DirectoryQuery("username", "offline", null)));
    }
}
