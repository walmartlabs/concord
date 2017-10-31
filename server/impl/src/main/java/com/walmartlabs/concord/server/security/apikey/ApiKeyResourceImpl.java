package com.walmartlabs.concord.server.security.apikey;

import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.security.apikey.DeleteApiKeyResponse;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authz.UnauthorizedException;
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
    public CreateApiKeyResponse create(CreateApiKeyRequest request) {
        UUID userId = assertUserId(request.getUserId());
        if (userId == null) {
            userId = assertUsername(request.getUsername());
        }

        if (userId == null) {
            throw new ValidationErrorsException("User ID or name is required");
        }

        assertOwner(userId);

        String key = apiKeyDao.newApiKey();
        UUID id = apiKeyDao.insert(userId, key);
        return new CreateApiKeyResponse(id, key);
    }

    @Override
    @Validate
    public DeleteApiKeyResponse delete(UUID id) {
        UUID userId = apiKeyDao.getUserId(id);
        if (userId == null) {
            throw new ValidationErrorsException("API key not found: " + id);
        }

        assertOwner(userId);

        apiKeyDao.delete(id);
        return new DeleteApiKeyResponse();
    }

    private UUID assertUsername(String username) {
        if (username == null) {
            return null;
        }

        if (!userManager.getId(username).isPresent()) {
            try {
                if (ldapManager.getInfo(username) == null) {
                    throw new ValidationErrorsException("LDAP user not found: " + username);
                }
            } catch (NamingException e) {
                throw new WebApplicationException(e);
            }
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

    private void assertOwner(UUID userId) {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (p.isAdmin()) {
            // admin users can manage other user's keys
            return;
        }

        if (!userId.equals(p.getId())) {
            throw new UnauthorizedException("Operation is not permitted");
        }
    }
}
