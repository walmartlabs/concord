package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.user.RoleEntry;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoleDaoTest extends AbstractDaoTest {

    @Test
    public void testGetPermissions() throws Exception {
        RoleDao roleDao = new RoleDao(getConfiguration());
        roleDao.getPermissions(Arrays.asList("a", "b", "c"));
    }

    @Test
    public void testInsertList() throws Exception {
        RoleDao roleDao = new RoleDao(getConfiguration());

        // ---

        roleDao.insert("roleA", "testA", Arrays.asList("a", "b"));
        roleDao.insert("roleB", "testB", Arrays.asList("b", "c"));
        roleDao.insert("roleC", "testC", Arrays.asList("b", "c", "d"));

        // ---

        Collection<String> perms = roleDao.getPermissions(Arrays.asList("roleA", "roleB", "roleC"));
        assertEquals(4, perms.size());

        // ---

        List<RoleEntry> l = roleDao.list();
        assertEquals(4, l.size()); // our 4 + 1 default

        RoleEntry eA = l.get(1);
        assertEquals("roleA", eA.getName());
        assertEquals(2, eA.getPermissions().size());
        assertTrue(eA.getPermissions().contains("a"));

        RoleEntry eC = l.get(3);
        assertEquals("testC", eC.getDescription());
        assertEquals(3, eC.getPermissions().size());
        assertTrue(eC.getPermissions().contains("d"));

        // ---

        roleDao.delete("roleB");
        l = roleDao.list();
        assertEquals(3, l.size()); // out 2 + 1 default
    }
}
