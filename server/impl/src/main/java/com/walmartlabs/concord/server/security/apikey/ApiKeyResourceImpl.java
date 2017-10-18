package com.walmartlabs.concord.server.security.apikey;

import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.security.apikey.DeleteApiKeyResponse;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingException;
import javax.ws.rs.WebApplicationException;
import java.util.UUID;

@Named
public class ApiKeyResourceImpl implements ApiKeyResource, Resource {

    private final ApiKeyDao apiKeyDao;
    private final UserManager userManager;
    private final LdapManager ldapManager;

    @Inject
    public ApiKeyResourceImpl(ApiKeyDao apiKeyDao, UserManager userManager, LdapManager ldapManager) {
        this.apiKeyDao = apiKeyDao;
        this.userManager = userManager;
        this.ldapManager = ldapManager;
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.APIKEY_CREATE_NEW)
    public CreateApiKeyResponse create(CreateApiKeyRequest request) {
        UUID userId = assertUserId(request.getUserId());
        if (userId == null) {
            userId = assertUsername(request.getUsername());
        }

        if (userId == null) {
            throw new ValidationErrorsException("User ID or name is required");
        }

        String key = apiKeyDao.newApiKey();
        UUID id = apiKeyDao.insert(userId, key);
        return new CreateApiKeyResponse(id, key);
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.APIKEY_DELETE_ANY)
    public DeleteApiKeyResponse delete(UUID id) {
        if (!apiKeyDao.existsById(id)) {
            throw new ValidationErrorsException("API key not found: " + id);
        }

        apiKeyDao.delete(id);
        return new DeleteApiKeyResponse();
    }

    private UUID assertUsername(String username) {
        if (username == null) {
            return null;
        }

        try {
            if (ldapManager.getInfo(username) == null) {
                throw new ValidationErrorsException("User not found: " + username);
            }
        } catch (NamingException e) {
            throw new WebApplicationException(e);
        }

        UserEntry entry = userManager.getOrCreate(username);
        return entry.getId();
    }

    private UUID assertUserId(UUID userId) {
        if (userId == null) {
            return null;
        }

        if (!userManager.get(userId).isPresent()) {
            throw new ValidationErrorsException("User not found: " + userId);
        }

        return userId;
    }
}
