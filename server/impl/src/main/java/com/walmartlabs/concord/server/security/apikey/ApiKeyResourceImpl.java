package com.walmartlabs.concord.server.security.apikey;

import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.security.apikey.DeleteApiKeyResponse;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class ApiKeyResourceImpl implements ApiKeyResource, Resource {

    private final ApiKeyDao apiKeyDao;
    private final UserDao userDao;

    @Inject
    public ApiKeyResourceImpl(ApiKeyDao apiKeyDao, UserDao userDao) {
        this.apiKeyDao = apiKeyDao;
        this.userDao = userDao;
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.APIKEY_CREATE_NEW)
    public CreateApiKeyResponse create(CreateApiKeyRequest request) {
        if (!userDao.existsById(request.getUserId())) {
            throw new ValidationErrorsException("User not found: " + request.getUserId());
        }

        String id = UUID.randomUUID().toString();
        String key = apiKeyDao.newApiKey();
        apiKeyDao.insert(id, request.getUserId(), key);
        return new CreateApiKeyResponse(id, key);
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.APIKEY_DELETE_ANY)
    public DeleteApiKeyResponse delete(String id) {
        if (!apiKeyDao.existsById(id)) {
            throw new ValidationErrorsException("API key not found: " + id);
        }

        apiKeyDao.delete(id);
        return new DeleteApiKeyResponse();
    }
}
