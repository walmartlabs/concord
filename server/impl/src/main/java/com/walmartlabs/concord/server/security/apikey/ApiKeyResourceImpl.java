package com.walmartlabs.concord.server.security.apikey;

import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.security.apikey.DeleteApiKeyResponse;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.util.UUID;

@Named
public class ApiKeyResourceImpl implements ApiKeyResource, Resource {

    private final ApiKeyDao apiKeyDao;

    @Inject
    public ApiKeyResourceImpl(ApiKeyDao apiKeyDao) {
        this.apiKeyDao = apiKeyDao;
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.APIKEY_CREATE_NEW)
    public CreateApiKeyResponse create(CreateApiKeyRequest request) {
        String id = UUID.randomUUID().toString();
        String key = apiKeyDao.newApiKey();
        apiKeyDao.insert(id, request.getUserId(), key);
        return new CreateApiKeyResponse(id, key);
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.APIKEY_DELETE_ANY)
    public DeleteApiKeyResponse delete(@PathParam("id") @ConcordId String id) {
        if (!apiKeyDao.existsById(id)) {
            throw new WebApplicationException("API key not found: " + id, Status.NOT_FOUND);
        }

        apiKeyDao.delete(id);
        return new DeleteApiKeyResponse();
    }
}
