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
import com.walmartlabs.concord.server.api.user.UserResource;
import com.walmartlabs.concord.server.api.user.UserType;
import org.junit.Test;

import javax.ws.rs.ForbiddenException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ApiKeyIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void testOwner() throws Exception {
        String userAName = "userA_" + randomString();
        String userBName = "userB_" + randomString();

        UserResource userResource = proxy(UserResource.class);
        userResource.createOrUpdate(new CreateUserRequest(userAName, UserType.LOCAL));
        userResource.createOrUpdate(new CreateUserRequest(userBName, UserType.LOCAL));

        // ---

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest(userAName));
        assertTrue(cakr.isOk());

        // ---

        setApiKey(cakr.getKey());

        try {
            apiKeyResource.create(new CreateApiKeyRequest(userBName));
            fail("Should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        cakr = apiKeyResource.create(new CreateApiKeyRequest(userAName));
        assertTrue(cakr.isOk());
    }
}
