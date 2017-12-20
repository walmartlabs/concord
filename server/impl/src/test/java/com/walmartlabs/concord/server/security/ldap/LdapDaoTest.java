package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.security.ldap.LdapMappingEntry;
import com.walmartlabs.concord.server.user.RoleDao;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Ignore("requires a local DB instance")
public class LdapDaoTest extends AbstractDaoTest {

    @Test
    public void testGetRoles() throws Exception {
        LdapDao ldapDao = new LdapDao(getConfiguration());
        ldapDao.getRoles(Arrays.asList("a", "b", "c"));
    }

    @Test
    public void testInsertList() throws Exception {
        RoleDao roleDao = new RoleDao(getConfiguration());
        roleDao.insert("a", "a", Collections.emptyList());
        roleDao.insert("b", "b", Collections.emptyList());
        roleDao.insert("c", "c", Collections.emptyList());
        roleDao.insert("d", "d", Collections.emptyList());

        // ---

        LdapDao ldapDao = new LdapDao(getConfiguration());

        // ---

        UUID mappingA = UUID.randomUUID();
        UUID mappingB = UUID.randomUUID();
        UUID mappingC = UUID.randomUUID();

        ldapDao.insert(mappingA, "testA", Arrays.asList("a", "b"));
        ldapDao.insert(mappingB, "testB", Arrays.asList("b", "c"));
        ldapDao.insert(mappingC, "testC", Arrays.asList("b", "c", "d"));

        // ---

        Collection<String> roles = ldapDao.getRoles(Arrays.asList("testA", "testB", "testC"));
        assertEquals(4, roles.size());

        // ---

        List<LdapMappingEntry> l = ldapDao.list();
        assertEquals(4, l.size()); // our 3 + 1 default

        LdapMappingEntry eA = l.get(1);
        assertEquals("testA", eA.getLdapDn());
        assertEquals(2, eA.getRoles().size());
        assertTrue(eA.getRoles().contains("a"));

        LdapMappingEntry eC = l.get(3);
        assertEquals("testC", eC.getLdapDn());
        assertEquals(3, eC.getRoles().size());
        assertTrue(eC.getRoles().contains("d"));

        // ---

        ldapDao.delete(mappingB);
        l = ldapDao.list();
        assertEquals(3, l.size()); // our 2 + 1 default
    }
}
