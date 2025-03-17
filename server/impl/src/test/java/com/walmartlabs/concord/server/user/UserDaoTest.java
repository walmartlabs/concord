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
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Disabled("requires a local DB instance")
public class UserDaoTest extends AbstractDaoTest {

    private UserDao userDao;
    private ApiKeyDao apiKeyDao;

    @BeforeEach
    public void setUp() {
        userDao = new UserDao(getConfiguration(), getUuidGenerator());
        apiKeyDao = new ApiKeyDao(getConfiguration(), new SecureRandomProvider().get(), getUuidGenerator());
    }

    @Test
    public void testInsertListDelete() {
        String username = "user#" + System.currentTimeMillis();

        UUID userId = userDao.insertOrUpdate(username, null, null, null, UserType.LOCAL, null);

        String s = "key#" + System.currentTimeMillis();
        String name = "name#" + System.currentTimeMillis();
        String apiKey = Base64.getEncoder().encodeToString(s.getBytes());
        apiKeyDao.insert(userId, apiKey, name, OffsetDateTime.now());

        // ---

        List<UserEntry> result = userDao.list(username, 0, 1);
        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getId());

        // ---

        userDao.delete(userId);

        // ---

        assertNull(userDao.get(userId));
        assertNull(apiKeyDao.find(apiKey));
    }
}
