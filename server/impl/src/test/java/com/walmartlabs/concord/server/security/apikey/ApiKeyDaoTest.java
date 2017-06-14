package com.walmartlabs.concord.server.security.apikey;

import com.walmartlabs.concord.server.AbstractDaoTest;
import org.junit.Ignore;
import org.junit.Test;

import java.security.SecureRandom;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

@Ignore("requires a local DB instance")
public class ApiKeyDaoTest extends AbstractDaoTest {

    @Test
    public void testDefaultAdminToken() throws Exception {
        ApiKeyDao m = new ApiKeyDao(getConfiguration(), mock(SecureRandom.class));
        String id = m.findUserId("auBy4eDWrKWsyhiDp3AQiw");
        assertNotNull(id);
    }
}
