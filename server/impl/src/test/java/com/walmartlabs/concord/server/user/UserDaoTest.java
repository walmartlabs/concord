package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.SecureRandomProvider;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertNull;

@Ignore("requires a local DB instance")
public class UserDaoTest extends AbstractDaoTest {

    private UserDao userDao;
    private ApiKeyDao apiKeyDao;

    @Before
    public void setUp() throws Exception {
        userDao = new UserDao(getConfiguration());
        apiKeyDao = new ApiKeyDao(getConfiguration(), new SecureRandomProvider().get());
    }

    @Test
    public void testInsertDelete() throws Exception {
        String username = "user#" + System.currentTimeMillis();
        Set<String> permissions = Collections.singleton("*");

        UUID userId = userDao.insert(username, permissions);

        String s = "key#" + System.currentTimeMillis();
        String apiKey = Base64.getEncoder().encodeToString(s.getBytes());
        apiKeyDao.insert(userId, apiKey);

        // ---

        userDao.delete(userId);

        // ---

        assertNull(userDao.get(userId));
        assertNull(apiKeyDao.findUserId(apiKey));
    }
}
