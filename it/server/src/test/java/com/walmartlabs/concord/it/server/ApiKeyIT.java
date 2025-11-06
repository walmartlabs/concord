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

import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ApiKeyIT extends AbstractServerIT {

    @Test
    public void testOwner() throws Exception {
        String userAName = "userA_" + randomString();
        String userBName = "userB_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userAName)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userBName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));
        assertTrue(cakr.getOk());

        // ---

        setApiKey(cakr.getKey());

        try {
            apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userBName));
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        cakr = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));
        assertTrue(cakr.getOk());
    }

    @Test
    public void testCreatingKeyWithoutUsername() throws Exception {
        String userName = "userA_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse user = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        // the new user has no api keys initially

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        List<ApiKeyEntry> keys = apiKeyResource.listUserApiKeys(user.getId());
        assertEquals(0, keys.size());

        // admin creates a new api key for the new user

        CreateApiKeyResponse cakr = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userName));
        assertTrue(cakr.getOk());
        keys = apiKeyResource.listUserApiKeys(user.getId());
        assertEquals(1, keys.size());

        // the new user creates another api key for themselves

        setApiKey(cakr.getKey());
        cakr = apiKeyResource.createUserApiKey(new CreateApiKeyRequest());
        assertTrue(cakr.getOk());

        // the new user lists all their api keys (should be 2)

        keys = apiKeyResource.listUserApiKeys(user.getId());
        assertEquals(2, keys.size());
    }
}
