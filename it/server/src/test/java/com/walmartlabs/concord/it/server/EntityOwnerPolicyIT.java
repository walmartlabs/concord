package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class EntityOwnerPolicyIT extends AbstractServerIT {

    private final String userOwner = "ownerUser_" + randomString();
    private final String policyName = "policy_" + randomString();

    @BeforeEach
    public void init() throws Exception {

        // --- policy
        Map<String, Object> ownerConditions = new HashMap<>();
        ownerConditions.put("username", userOwner);
        ownerConditions.put("userType", "LOCAL");
        Map<String, Object> deny = new HashMap<>();
        deny.put("deny", Collections.singletonList(createRule("create", "org",
                Collections.singletonMap("owner", ownerConditions))));
        Map<String, Object> rules = new HashMap<>();
        rules.put("entity", deny);
        createPolicy(null, null, rules);
    }

    @AfterEach
    public void cleanup() throws Exception {
        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.deletePolicy(policyName);
    }

    @Test
    public void testOrgCreation() throws Exception {
        // --- user
        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userOwner)
                .email("owner@mail.com")
                .displayName("Test Owner")
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(userOwner));
        assertTrue(cakr.getOk());

        usersApi.updateUserRoles(userOwner, new UpdateUserRolesRequest()
                .roles(Collections.singleton("concordAdmin")));

        // ---

        setApiKey(cakr.getKey());

        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        EntityOwner owner = new EntityOwner();
        owner.setUsername(userOwner);

        try {
            orgApi.createOrUpdateOrg(new OrganizationEntry().owner(owner).name(orgName));
            fail("exception expected");
        } catch (ApiException e) {
            assertTrue(e.getResponseBody().contains("Action forbidden: test-rule"));
        }
    }

    private Map<String, Object> createRule(String action, String entity, Map<String, Object> conditions) {
        Map<String, Object> result = new HashMap<>();
        result.put("msg", "test-rule");
        result.put("action", action);
        result.put("entity", entity);
        result.put("conditions", conditions);
        return result;
    }

    private String createPolicy(String orgName, String projectName, Map<String, Object> rules) throws ApiException {
        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.createOrUpdatePolicy(new PolicyEntry()
                .name(policyName)
                .rules(rules));

        policyApi.linkPolicy(policyName, new PolicyLinkEntry()
                .orgName(orgName)
                .projectName(projectName));

        return policyName;
    }
}
