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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ApiKeyIT extends AbstractServerIT {

    @Test
    public void testOwner() throws Exception {
        String userAName = "userA_" + randomString();
        String userBName = "userB_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userAName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userBName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));
        assertTrue(cakr.isOk());

        // ---

        setApiKey(cakr.getKey());

        try {
            apiKeyResource.create(new CreateApiKeyRequest().setUsername(userBName));
            fail("Should fail");
        } catch (ApiException e) {
        }

        // ---

        cakr = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));
        assertTrue(cakr.isOk());
    }
}
