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

import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ApiKeysApi;
import com.walmartlabs.concord.client2.CreateApiKeyRequest;
import com.walmartlabs.concord.client2.CreateUserRequest;
import com.walmartlabs.concord.client2.UpdateUserRolesRequest;
import com.walmartlabs.concord.client2.UserEntry;
import com.walmartlabs.concord.client2.UserV2Api;
import com.walmartlabs.concord.client2.UsersApi;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserResourceV2IT extends AbstractServerIT {

    @Test
    void testGetUser() throws Exception {
        // user with no roles

        var userAName = "user_basic_" + randomString();
        var noRolesUser = addUser(userAName, Set.of());

        // user with system reader role

        var userBName = "user_system_reader_" + randomString();
        var systemReaderUser = addUser(userBName, Set.of("concordSystemReader"));

        // get a user with insufficient privileges
        var ex = assertThrows(ApiException.class, () -> getUser(noRolesUser, systemReaderUser.userId()));
        assertTrue(ex.getMessage().contains("Users can only view their own information or must have admin privileges."));

        // get a user with concordSystemReader role
        var user = assertDoesNotThrow(() -> getUser(systemReaderUser, noRolesUser.userId()));
        assertEquals(user.getId(), noRolesUser.userId());
    }

    private UserEntry getUser(UserInfo userInfo, UUID userToGet) throws ApiException {
        var apiClient = new UserV2Api(getApiClientForKey(userInfo.apiKey()));

        return apiClient.getUser(userToGet);
    }

    private UserInfo addUser(String username, Set<String> roles) throws ApiException {
        var usersApi = new UsersApi(getApiClient());
        var user = usersApi.createOrUpdateUser(new CreateUserRequest().username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        if (!roles.isEmpty()) {
            usersApi.updateUserRoles(username, new UpdateUserRolesRequest()
                    .roles(roles));
        }

        var apiKeysApi = new ApiKeysApi(getApiClient());
        var apiKeyResp = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                .userId(user.getId()));

        return new UserInfo(username, user.getId(), apiKeyResp.getKey());
    }

    private record UserInfo(String username, UUID userId, String apiKey) { }
}
