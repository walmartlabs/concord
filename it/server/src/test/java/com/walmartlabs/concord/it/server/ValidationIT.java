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
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static org.junit.jupiter.api.Assertions.*;

public class ValidationIT extends AbstractServerIT {

    @Test
    public void testValidationExceptionMapping() throws Exception {
        var processApi = new ProcessApi(getApiClient());
        var input = Map.<String, Object>of(Constants.Multipart.ORG_NAME, "org_" + randomString(),
                Constants.Multipart.PROJECT_NAME, "foo",
                "archive", archive(ProcessIT.class.getResource("example").toURI()));
        var ex = assertThrows(ApiException.class, () -> processApi.startProcess(input));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("Organization not found"));
    }

    @Test
    public void testProjectCreation() throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        try {
            ProjectEntry req = new ProjectEntry().name("@123_123");
            projectsApi.createOrUpdateProject("Default", req);
            fail("Should fail with a validation error");
        } catch (ApiException e) {
            assertInvalidRequest(e);
        }

        ProjectEntry req = new ProjectEntry().name("aProperName@" + System.currentTimeMillis());
        projectsApi.createOrUpdateProject("Default", req);
    }

    @Test
    public void testInvalidUsername() {
        String longUsername = "01234567890123456789012345678901234567890123456789012345678901234567890123456789" +
                "01234567890123456789012345678901234567890123456789012345678901234567890123456789";

        UsersApi usersApi = new UsersApi(getApiClient());

        try {
            usersApi.findByUsername("test@localhost");
            fail("Should fail with a validation error");
        } catch (ApiException e) {
            assertInvalidRequest(e);
        }

        try {
            usersApi.findByUsername("local\\test");
            fail("Should fail with a validation error");
        } catch (ApiException e) {
            assertInvalidRequest(e);
        }

        try {
            usersApi.findByUsername(longUsername);
            fail("Should fail with a validation error");
        } catch (ApiException e) {
            assertInvalidRequest(e);
        }

        try {
            usersApi.findByUsername("test#" + System.currentTimeMillis());
            fail("Random valid username, should fail with 404");
        } catch (ApiException e) {
            assertInvalidRequest(e);
        }

        try {
            usersApi.createOrUpdateUser(new CreateUserRequest().username(longUsername)
                    .type(CreateUserRequest.TypeEnum.LOCAL));
            fail("Should fail with a validation error");
        } catch (ApiException e) {
            assertInvalidRequest(e);
        }
    }

    private static void assertInvalidRequest(ApiException e) {
        int code = e.getCode();
        assertTrue(code >= 400 && code < 500);
    }
}
