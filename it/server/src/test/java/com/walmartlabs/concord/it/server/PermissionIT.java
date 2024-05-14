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

import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PermissionIT extends AbstractServerIT {

    @Test
    public void testCreateOrgPermission() throws Exception {
        String userName = "user_" + randomString();
        String roleName = "role_" + randomString();
        String orgNameA = "org_" + randomString();
        String orgNameB = "org_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        RolesApi rolesApi = new RolesApi(getApiClient());

        // -- Create role with permission to create orgs

        Set<String> permissions = new HashSet<>();
        permissions.add("createOrg");

        RoleOperationResponse rop = rolesApi.createOrUpdateRole(new RoleEntry()
                .name(roleName)
                .permissions(permissions));

        // -- Create user with the role

        Set<String> roles = new HashSet<>();
        roles.add(roleName);

        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .type(CreateUserRequest.TypeEnum.LOCAL)
                .username(userName)
                .roles(roles));


        // -- Switch to new user's api key
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeysApi.createUserApiKey(new CreateApiKeyRequest().username(userName));

        setApiKey(apiKeyA.getKey());


        // -- Create the org

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        CreateOrganizationResponse cor1 = organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgNameA));

        assertTrue(cor1.getOk());


        // -- Remove role (and permission) from user

        resetApiKey();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .type(CreateUserRequest.TypeEnum.LOCAL)
                .username(userName)
                .roles(Collections.emptySet())
        );


        // -- Org creation must be denied

        setApiKey(apiKeyA.getKey());
        try {
            organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgNameB));
            fail("users without creatOrg permissions must not be allowed to create orgs.");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("'createOrg' permission"));
        }
    }

    @Test
    public void testUpdateOrgPermission() throws Exception {
        String userNameA = "userA_" + randomString();
        String userNameB = "userB_" + randomString();
        String roleName = "role_" + randomString();
        String orgName = "org_" + randomString();


        // -- Create role with permission to update orgs

        RolesApi rolesApi = new RolesApi(getApiClient());
        RoleOperationResponse role = rolesApi.createOrUpdateRole(new RoleEntry()
                .name(roleName)
                .permissions(Collections.singleton("updateOrg")));

        // -- Create user with the role

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse userA = usersApi.createOrUpdateUser(new CreateUserRequest()
                .type(CreateUserRequest.TypeEnum.LOCAL)
                .username(userNameA)
                .roles(Collections.singleton(roleName)));

        // -- Create the org

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        CreateOrganizationResponse cor1 = organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        assertTrue(cor1.getOk());
        assertEquals("admin", organizationsApi.getOrg(orgName).getOwner().getUsername());

        // -- Switch to new user's api key
        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse userAKey = apiKeysApi.createUserApiKey(new CreateApiKeyRequest().username(userNameA));

        setApiKey(userAKey.getKey());

        // update org owner

        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .owner(new EntityOwner().id(userA.getId())));

        assertEquals(userA.getId(), organizationsApi.getOrg(orgName).getOwner().getId());

        // -- Remove role (and permission) from user

        resetApiKey();
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .type(CreateUserRequest.TypeEnum.LOCAL)
                .username(userNameA)
                .roles(Collections.emptySet())
        );

        // new user - userB

        CreateUserResponse userB = usersApi.createOrUpdateUser(new CreateUserRequest()
                .type(CreateUserRequest.TypeEnum.LOCAL)
                .username(userNameB));

        // change owner: userA -> userB

        setApiKey(userAKey.getKey());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName)
                .owner(new EntityOwner().id(userB.getId())));

        assertEquals(userB.getId(), organizationsApi.getOrg(orgName).getOwner().getId());

        // -- Org update must be denied

        try {
            organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName).visibility(OrganizationEntry.VisibilityEnum.PRIVATE));
            fail("users without updateOrg permissions must not be allowed to create orgs.");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("'updateOrg' permission"));
        }

        // cleanup
        resetApiKey();
        organizationsApi.deleteOrg(orgName, "yes");
        usersApi.deleteUser(userA.getId());
        usersApi.deleteUser(userB.getId());
        rolesApi.deleteRole(roleName);
    }
}
