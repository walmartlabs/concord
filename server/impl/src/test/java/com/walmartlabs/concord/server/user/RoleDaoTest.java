package com.walmartlabs.concord.server.user;

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
import com.walmartlabs.concord.server.api.user.RoleEntry;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Ignore("requires a local DB instance")
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
