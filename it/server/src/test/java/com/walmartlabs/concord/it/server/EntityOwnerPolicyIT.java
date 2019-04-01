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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EntityOwnerPolicyIT extends AbstractServerIT {

    private final String userOwner = "ownerUser_" + randomString();
    private final String policyName = "policy_" + randomString();

    @Before
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

    @After
    public void cleanup() throws Exception {
        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.delete(policyName);
    }

    @Test
    public void testOrgCreation() throws Exception {
        // --- user
        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userOwner)
                .setEmail("owner@mail.com")
                .setDisplayName("Test Owner")
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userOwner));
        assertTrue(cakr.isOk());

        usersApi.updateUserRoles(userOwner, new UpdateUserRolesRequest()
                .setRoles(Collections.singletonList("concordAdmin")));

        // ---

        setApiKey(cakr.getKey());

        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        EntityOwner owner = new EntityOwner();
        owner.setUsername(userOwner);

        try {
            orgApi.createOrUpdate(new OrganizationEntry().setOwner(owner).setName(orgName));
            fail("exception expected");
        } catch (ApiException e) {
            System.out.println(e.getResponseBody());
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
        policyApi.createOrUpdate(new PolicyEntry()
                .setName(policyName)
                .setRules(rules));

        policyApi.link(policyName, new PolicyLinkEntry()
                .setOrgName(orgName)
                .setProjectName(projectName));

        return policyName;
    }
}
