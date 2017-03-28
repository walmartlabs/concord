package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.AbstractDaoTest;
import org.junit.Test;

import java.util.Arrays;

public class RoleDaoTest extends AbstractDaoTest {

    @Test
    public void testGetPermissions() throws Exception {
        RoleDao roleDao = new RoleDao(getConfiguration());
        roleDao.getPermissions(Arrays.asList("a", "b", "c"));
    }
}
