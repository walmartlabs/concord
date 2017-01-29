package com.walmartlabs.concord.server.security.apikey;

import com.walmartlabs.concord.server.AbstractDaoTest;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class ApiKeyDaoTest extends AbstractDaoTest {

    @Test
    public void testDefaultAdminToken() throws Exception {
        ApiKeyDao m = new ApiKeyDao(getConfiguration());
        String id = m.findUserId("auBy4eDWrKWsyhiDp3AQiw");
        assertNotNull(id);
    }
}
