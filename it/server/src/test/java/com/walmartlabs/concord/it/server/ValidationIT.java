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
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.user.UserResource;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;

import static org.junit.Assert.fail;

public class ValidationIT extends AbstractServerIT {

    @Test(expected = BadRequestException.class)
    public void testApiKeys() {
        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);

        CreateApiKeyRequest req = new CreateApiKeyRequest(null, null);
        apiKeyResource.create(req);
    }

    @Test
    public void testProjectCreation() {
        ProjectResource projectResource = proxy(ProjectResource.class);

        try {
            ProjectEntry req = new ProjectEntry("@123_123");
            projectResource.createOrUpdate(req);
            fail("Should fail with validation error");
        } catch (BadRequestException e) {
        }

        ProjectEntry req = new ProjectEntry("aProperName@" + System.currentTimeMillis());
        projectResource.createOrUpdate(req);
    }

    @Test
    public void testInvalidUsername() {
        UserResource userResource = proxy(UserResource.class);

        try {
            userResource.findByUsername("test@localhost");
            fail("Should fail with validation error");
        } catch (BadRequestException e) {
        }

        try {
            userResource.findByUsername("local\\test");
            fail("Should fail with validation error");
        } catch (BadRequestException e) {
        }

        try {
            userResource.findByUsername("test#" + System.currentTimeMillis());
            fail("Random valid username, should fail with 404");
        } catch (NotFoundException e) {
        }
    }
}
