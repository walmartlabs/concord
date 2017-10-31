package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import com.walmartlabs.concord.server.api.user.UserResource;
import org.junit.Test;

import javax.ws.rs.ForbiddenException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ApiKeyIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void testOwner() throws Exception {
        String userAName = "userA_" + System.currentTimeMillis();
        String userBName = "userB_" + System.currentTimeMillis();

        UserResource userResource = proxy(UserResource.class);
        userResource.createOrUpdate(new CreateUserRequest(userAName));
        userResource.createOrUpdate(new CreateUserRequest(userBName));

        // ---

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest(null, userAName));
        assertTrue(cakr.isOk());

        // ---

        setApiKey(cakr.getKey());

        try {
            apiKeyResource.create(new CreateApiKeyRequest(null, userBName));
            fail("Should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        cakr = apiKeyResource.create(new CreateApiKeyRequest(null, userAName));
        assertTrue(cakr.isOk());
    }
}
