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

import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.sdk.security.DirectoryAmbiguousException;
import com.walmartlabs.concord.server.sdk.security.DirectoryUnavailableException;
import com.walmartlabs.concord.server.sdk.security.DirectoryQuery;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.junit.jupiter.api.Test;

import javax.naming.CommunicationException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import java.time.Duration;
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
        when(manager.getPrincipal("ambiguous", null)).thenThrow(new LdapNonUniqueResultException("non unique result"));
        when(manager.getPrincipal("offline", null)).thenThrow(new CommunicationException("offline"));
        LdapUserDirectory directory = new LdapUserDirectory(manager);
        assertTrue(directory.find(new DirectoryQuery("username", "missing", null)).isEmpty());
        assertThrows(DirectoryAmbiguousException.class, () -> directory.find(new DirectoryQuery("username", "ambiguous", null)));
        assertThrows(DirectoryUnavailableException.class, () -> directory.find(new DirectoryQuery("username", "offline", null)));
    }

    @Test
    void groupLookupAcceptsCaseInsensitiveLdapAttributeNames() throws Exception {
        LdapConfiguration cfg = mock(LdapConfiguration.class);
        LdapContextFactory factory = mock(LdapContextFactory.class);
        LdapContext context = mock(LdapContext.class);
        @SuppressWarnings("unchecked")
        NamingEnumeration<SearchResult> results = mock(NamingEnumeration.class);
        var attributes = new BasicAttributes();
        attributes.put(new BasicAttribute("memberof", "cn=developers,dc=example,dc=org"));
        when(cfg.getSearchBase()).thenReturn("dc=example,dc=org");
        when(cfg.getPrincipalSearchFilter()).thenReturn("(cn={0})");
        when(factory.getSystemLdapContext()).thenReturn(context);
        when(context.search(eq("dc=example,dc=org"), eq("(cn={0})"), any(Object[].class), any(SearchControls.class)))
                .thenReturn(results);
        when(results.hasMoreElements()).thenReturn(true, true, false);
        when(results.next()).thenReturn(new SearchResult("cn=alice", null, attributes));

        assertEquals(Set.of("cn=developers,dc=example,dc=org"),
                new LdapManagerImpl(cfg, factory).getGroups("alice", null));
    }

    @Test
    void cachedLookupsPreserveAmbiguityAndDistinguishOutages() throws Exception {
        LdapManager delegate = mock(LdapManager.class);
        when(delegate.getPrincipal("ambiguous", null)).thenThrow(new LdapNonUniqueResultException("duplicate username"));
        when(delegate.getPrincipalByMail("ambiguous@example")).thenThrow(new LdapNonUniqueResultException("duplicate email"));
        when(delegate.getPrincipalByDn("cn=ambiguous,dc=example")).thenThrow(new LdapNonUniqueResultException("duplicate DN"));
        when(delegate.getPrincipal("offline", null)).thenThrow(new CommunicationException("offline"));
        when(delegate.getPrincipal("missing", null)).thenReturn(null);
        LdapUserDirectory directory = new LdapUserDirectory(new CachingLdapManager(Duration.ofMinutes(1), delegate));

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThrows(DirectoryAmbiguousException.class,
                    () -> directory.find(new DirectoryQuery("username", "ambiguous", null)));
            assertThrows(DirectoryAmbiguousException.class,
                    () -> directory.find(new DirectoryQuery("email", "ambiguous@example", null)));
            assertThrows(DirectoryAmbiguousException.class,
                    () -> directory.find(new DirectoryQuery("distinguishedName", "cn=ambiguous,dc=example", null)));
            assertThrows(DirectoryAmbiguousException.class,
                    () -> directory.get(LdapUserDirectory.key("ambiguous", null)));
            assertThrows(DirectoryUnavailableException.class,
                    () -> directory.find(new DirectoryQuery("username", "offline", null)));
            assertTrue(directory.find(new DirectoryQuery("username", "missing", null)).isEmpty());
        }
        verify(delegate, times(4)).getPrincipal("ambiguous", null);
        verify(delegate, times(2)).getPrincipalByMail("ambiguous@example");
        verify(delegate, times(2)).getPrincipalByDn("cn=ambiguous,dc=example");
        verify(delegate, times(2)).getPrincipal("offline", null);
        verify(delegate).getPrincipal("missing", null);
    }
}
