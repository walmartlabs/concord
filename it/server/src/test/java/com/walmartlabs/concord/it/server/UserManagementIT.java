package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import com.walmartlabs.concord.server.api.user.CreateUserResponse;
import com.walmartlabs.concord.server.api.user.UserResource;
import org.junit.Test;

import javax.ws.rs.ForbiddenException;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UserManagementIT extends AbstractServerIT {

    @Test(timeout = 30000)
    public void test() throws Exception {
        UserResource userResource = proxy(UserResource.class);

        String username = "user_" + randomString();
        Set<String> permissions = Collections.singleton("user:delete");

        CreateUserResponse cur = userResource.createOrUpdate(new CreateUserRequest(username, permissions, false));
        assertTrue(cur.isOk());

        // ---

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest(username));
        assertTrue(cakr.isOk());

        // ---

        userResource.delete(cur.getId());
    }

    @Test(timeout = 30000)
    public void testAdmins() throws Exception {
        UserResource userResource = proxy(UserResource.class);

        String userAName = "userA_" + randomString();
        userResource.createOrUpdate(new CreateUserRequest(userAName, null, false));

        // ---

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse apiKey = apiKeyResource.create(new CreateApiKeyRequest(userAName));

        // ---

        setApiKey(apiKey.getKey());

        String userBName = "userB_" + randomString();
        try {
            userResource.createOrUpdate(new CreateUserRequest(userBName, null, false));
            fail("should fail");
        } catch (ForbiddenException e) {
        }

        // ---

        resetApiKey();
        userResource.createOrUpdate(new CreateUserRequest(userAName, null, true));

        // ---

        setApiKey(apiKey.getKey());
        userResource.createOrUpdate(new CreateUserRequest(userBName, null, false));
    }
}
