package com.walmartlabs.concord.it.server;

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

import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import com.walmartlabs.concord.server.api.user.CreateUserResponse;
import com.walmartlabs.concord.server.api.user.UserResource;
import com.walmartlabs.concord.server.api.user.UserType;
import org.junit.Test;

import javax.ws.rs.ForbiddenException;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UserManagementIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        UserResource userResource = proxy(UserResource.class);

        String username = "user_" + randomString();

        CreateUserResponse cur = userResource.createOrUpdate(new CreateUserRequest(username, UserType.LOCAL, false));
        assertTrue(cur.isOk());

        // ---

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest(username));
        assertTrue(cakr.isOk());

        // ---

        userResource.delete(cur.getId());
    }

    @Test(timeout = 60000)
    public void testAdmins() throws Exception {
        UserResource userResource = proxy(UserResource.class);

        String userAName = "userA_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userAName, UserType.LOCAL, false));

        // ---

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse apiKey = apiKeyResource.create(new CreateApiKeyRequest(userAName));

        // ---

        setApiKey(apiKey.getKey());

        String userBName = "userB_" + randomString();
        try {
            userResource.createOrUpdate(new CreateUserRequest(userBName, UserType.LOCAL, false));
            fail("should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        resetApiKey();
        userResource.createOrUpdate(new CreateUserRequest(userAName, UserType.LOCAL, true));

        // ---

        setApiKey(apiKey.getKey());
        userResource.createOrUpdate(new CreateUserRequest(userBName, UserType.LOCAL, false));
    }
}
