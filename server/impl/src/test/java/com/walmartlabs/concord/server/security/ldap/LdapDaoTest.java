package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.security.ldap.LdapMappingEntry;
import com.walmartlabs.concord.server.user.RoleDao;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

        ldapDao.insert("mappingA", "testA", Arrays.asList("a", "b"));
        ldapDao.insert("mappingB", "testB", Arrays.asList("b", "c"));
        ldapDao.insert("mappingC", "testC", Arrays.asList("b", "c", "d"));

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

        ldapDao.delete("mappingB");
        l = ldapDao.list();
        assertEquals(3, l.size()); // our 2 + 1 default
    }
}
