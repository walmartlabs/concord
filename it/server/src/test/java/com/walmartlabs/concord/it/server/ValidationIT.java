package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.project.CreateProjectRequest;
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

        CreateApiKeyRequest req = new CreateApiKeyRequest(null);
        apiKeyResource.create(req);
    }

    @Test
    public void testProjectCreation() {
        ProjectResource projectResource = proxy(ProjectResource.class);

        try {
            CreateProjectRequest req = new CreateProjectRequest("@123_123", null, null);
            projectResource.createOrUpdate(req);
            fail("Should fail with validation error");
        } catch (BadRequestException e) {
        }

        CreateProjectRequest req = new CreateProjectRequest("aProperName@" + System.currentTimeMillis(), null, null);
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
