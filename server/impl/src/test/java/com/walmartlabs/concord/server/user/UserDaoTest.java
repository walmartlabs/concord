package com.walmartlabs.concord.server.user;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.walmartlabs.concord.server.SecureRandomProvider;
import com.walmartlabs.concord.server.user.UserType;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Instant;
import java.util.Base64;
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

        UUID userId = userDao.insert(username, UserType.LOCAL, false);

        String s = "key#" + System.currentTimeMillis();
        String name = "name#" + System.currentTimeMillis();
        String apiKey = Base64.getEncoder().encodeToString(s.getBytes());
        apiKeyDao.insert(userId, apiKey, name, Instant.now());

        // ---

        userDao.delete(userId);

        // ---

        assertNull(userDao.get(userId));
        assertNull(apiKeyDao.findUserId(apiKey));
    }
}
