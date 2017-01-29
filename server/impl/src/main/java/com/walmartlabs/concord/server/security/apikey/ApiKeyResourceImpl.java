package com.walmartlabs.concord.server.security.apikey;

import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.security.Permissions;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;

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
        String key = apiKeyDao.newApiKey();
        apiKeyDao.insert(request.getUserId(), key);
        return new CreateApiKeyResponse(key);
    }
}
