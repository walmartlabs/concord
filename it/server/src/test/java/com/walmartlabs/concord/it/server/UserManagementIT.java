package com.walmartlabs.concord.it.server;

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


import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UserManagementIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());

        String username = "user_" + randomString();

        CreateUserResponse cur = usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(username)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        assertTrue(cur.isOk());

        // ---

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest().setUsername(username));
        assertTrue(cakr.isOk());

        // ---

        usersApi.delete(cur.getId());
    }

    @Test(timeout = 60000)
    public void testAdmins() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userAName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKey = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        // ---

        setApiKey(apiKey.getKey());

        String userBName = "userB_" + randomString();
        try {
            usersApi.createOrUpdate(new CreateUserRequest()
                    .setUsername(userBName)
                    .setType(CreateUserRequest.TypeEnum.LOCAL));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userAName)
                .setType(CreateUserRequest.TypeEnum.LOCAL)
                .setAdmin(true));

        // ---

        setApiKey(apiKey.getKey());
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userBName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
    }
}
