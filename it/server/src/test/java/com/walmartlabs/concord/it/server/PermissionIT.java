package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.client.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PermissionIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCreateOrgPermission() throws Exception {
        String userName = "user_" + randomString();
        String roleName = "role_" + randomString();
        String orgNameA = "org_" + randomString();
        String orgNameB = "org_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        RolesApi rolesApi = new RolesApi(getApiClient());

        // -- Create role with permission to create orgs

        List<String> permissions = new ArrayList<>();
        permissions.add("createOrg");

        RoleOperationResponse rop = rolesApi.createOrUpdate(new RoleEntry()
                .setName(roleName)
                .setPermissions(permissions));

        // -- Create user with the role

        List<String> roles = new ArrayList<>();
        roles.add(roleName);

        CreateUserResponse cur = usersApi.createOrUpdate(new CreateUserRequest()
                .setType(CreateUserRequest.TypeEnum.LOCAL)
                .setUsername(userName)
                .roles(roles));


        // -- Switch to new user's api key
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeysApi.create(new CreateApiKeyRequest().setUsername(userName));

        setApiKey(apiKeyA.getKey());


        // -- Create the org

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        CreateOrganizationResponse cor1 = organizationsApi.createOrUpdate(new OrganizationEntry().setName(orgNameA));

        assertTrue(cor1.isOk());


        // -- Remove role (and permission) from user

        resetApiKey();
        usersApi.createOrUpdate(new CreateUserRequest()
                .setType(CreateUserRequest.TypeEnum.LOCAL)
                .setUsername(userName)
                .setRoles(Collections.emptyList())
        );


        // -- Org creation must be denied

        setApiKey(apiKeyA.getKey());
        try {
            organizationsApi.createOrUpdate(new OrganizationEntry().setName(orgNameB));
            fail("users without creatOrg permissions must not be allowed to create orgs.");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("'createOrg' permission"));
        }

    }
}
