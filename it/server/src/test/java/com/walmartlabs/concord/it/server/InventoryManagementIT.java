package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.plugins.ansible.inventory.api.CreateInventoryResponse;
import com.walmartlabs.concord.plugins.ansible.inventory.api.InventoryEntry;
import com.walmartlabs.concord.plugins.ansible.inventory.api.InventoryResource;
import com.walmartlabs.concord.plugins.ansible.inventory.api.Permissions;
import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.user.*;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.*;

public class InventoryManagementIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        // admin: create an inventory file

        String inventoryName = "inv#" + System.currentTimeMillis();
        byte[] data = {0, 1, 2, 3};

        InventoryResource inventoryResource = proxy(InventoryResource.class);
        CreateInventoryResponse cir = inventoryResource.create(inventoryName, new ByteArrayInputStream(data));
        assertTrue(cir.isOk());

        // admin: create a new user, with read-only access to the inventory file

        String username = "user#" + System.currentTimeMillis();
        Set<String> permissions = Collections.singleton(String.format(Permissions.INVENTORY_USE_INSTANCE, inventoryName));

        UserResource userResource = proxy(UserResource.class);
        CreateUserResponse cur = userResource.create(new CreateUserRequest(username, permissions));
        assertTrue(cur.isOk());

        // admin: create a new API key for the user

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest(cur.getId()));
        assertTrue(cakr.isOk());

        // switch to the created user

        setApiKey(cakr.getKey());

        // user: get inventory file

        String inventoryId = cir.getId();
        InventoryEntry e = inventoryResource.get(inventoryId);
        assertNotNull(e);
        assertEquals(inventoryName, e.getName());
        assertTrue(e.isReadOnly());

        // switch to admin

        setApiKey(DEFAULT_API_KEY);

        // admin: grant read-write permissions to the user

        permissions = Collections.singleton(String.format(Permissions.INVENTORY_MANAGE_INSTANCE, inventoryName));
        UpdateUserResponse uur = userResource.update(cur.getId(), new UpdateUserRequest(permissions));
        assertTrue(uur.isOk());

        // switch to the user

        setApiKey(cakr.getKey());

        // user: get inventory file

        e = inventoryResource.get(inventoryId);
        assertFalse(e.isReadOnly());
    }
}
