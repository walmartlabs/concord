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

import static org.junit.Assert.fail;

public class ValidationIT extends AbstractServerIT {

    @Test(expected = ApiException.class)
    public void testApiKeys() throws Exception {
        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());

        CreateApiKeyRequest req = new CreateApiKeyRequest();
        apiKeyResource.create(req);
    }

    @Test
    public void testProjectCreation() throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        try {
            ProjectEntry req = new ProjectEntry().setName("@123_123");
            projectsApi.createOrUpdate("Default", req);
            fail("Should fail with validation error");
        } catch (ApiException e) {
        }

        ProjectEntry req = new ProjectEntry().setName("aProperName@" + System.currentTimeMillis());
        projectsApi.createOrUpdate("Default", req);
    }

    @Test
    public void testInvalidUsername() throws Exception {
        UsersApi usersApi = new UsersApi(getApiClient());

        try {
            usersApi.findByUsername("test@localhost");
            fail("Should fail with validation error");
        } catch (ApiException e) {
        }

        try {
            usersApi.findByUsername("local\\test");
            fail("Should fail with validation error");
        } catch (ApiException e) {
        }

        try {
            usersApi.findByUsername("test#" + System.currentTimeMillis());
            fail("Random valid username, should fail with 404");
        } catch (ApiException e) {
        }
    }
}
