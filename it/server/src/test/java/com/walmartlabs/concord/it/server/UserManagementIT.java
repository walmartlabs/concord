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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class UserManagementIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());

        String username = "user_" + randomString();

        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        assertTrue(cur.getOk());

        // ---

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(username));
        assertTrue(cakr.getOk());

        // ---

        usersApi.deleteUser(cur.getId());
    }

    @Test
    public void testAdmins() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userAName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKey = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userAName));

        // ---

        setApiKey(apiKey.getKey());

        String userBName = "userB_" + randomString();
        try {
            usersApi.createOrUpdateUser(new CreateUserRequest()
                    .username(userBName)
                    .type(CreateUserRequest.TypeEnum.LOCAL));
            fail("should fail");
        } catch (ApiException e) {
        }

        // ---

        resetApiKey();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userAName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        usersApi.updateUserRoles(userAName, new UpdateUserRolesRequest()
                .roles(Collections.singleton("concordAdmin")));

        // ---

        setApiKey(apiKey.getKey());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userBName)
                .type(CreateUserRequest.TypeEnum.LOCAL));
    }

    @Test
    public void testWithRoles() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());

        String roleName = "role_" + randomString();
        String username = "user_" + randomString();

        RolesApi rolesApi = new RolesApi(getApiClient());
        RoleOperationResponse ror = rolesApi.createOrUpdateRole(new RoleEntry().name(roleName));
        assertEquals(RoleOperationResponse.ResultEnum.CREATED, ror.getResult());

        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL)
                .roles(Collections.singleton(roleName)));
        assertTrue(cur.getOk());

        UserEntry userEntry = usersApi.findByUsername(username);
        assertNotNull(userEntry);
        assertEquals(roleName, userEntry.getRoles().iterator().next().getName());

        // ---

        DeleteUserResponse dur = usersApi.deleteUser(cur.getId());
        assertTrue(dur.getOk());

        GenericOperationResult delete = rolesApi.deleteRole(roleName);
        assertEquals(GenericOperationResult.ResultEnum.DELETED, delete.getResult());
    }

    @Test
    public void testSpecialCharactersInUsernames() throws Exception {
        String userName = "usEr_" + randomString() + "@domain.local";

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        assertNotNull(cur.getId());

        UserEntry e = usersApi.findByUsername(userName);
        assertEquals(userName.toLowerCase(), e.getName());
    }
}
