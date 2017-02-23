package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import com.walmartlabs.concord.server.api.user.CreateUserResponse;
import com.walmartlabs.concord.server.api.user.UserResource;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class UserManagementIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        UserResource userResource = proxy(UserResource.class);

        String username = "user@" + System.currentTimeMillis();
        Set<String> permissions = Collections.singleton("user:delete");

        CreateUserResponse cur = userResource.create(new CreateUserRequest(username, permissions));
        assertTrue(cur.isOk());

        // ---

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest(cur.getId()));
        assertTrue(cakr.isOk());

        // ---

        setApiKey(cakr.getKey());
        userResource.delete(cur.getId());
    }
}
