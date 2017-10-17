package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.api.PerformedActionType;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.user.*;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.util.UUID;

@Named
public class UserResourceImpl implements UserResource, Resource {

    private final UserManager userManager;
    private final UserDao userDao;

    @Inject
    public UserResourceImpl(UserManager userManager, UserDao userDao) {
        this.userManager = userManager;
        this.userDao = userDao;
    }

    @Override
    @Validate
    public CreateUserResponse createOrUpdate(CreateUserRequest request) {
        String username = request.getUsername();

        UUID id = userDao.getId(username);
        if (id == null) {
            assertPermissions(Permissions.USER_CREATE_NEW, "The current user does not have permissions to create a new user");
            UserEntry e = userManager.create(username, request.getPermissions());
            return new CreateUserResponse(e.getId(), PerformedActionType.CREATED);
        } else {
            // TODO check per-entry permissions?
            assertPermissions(Permissions.USER_UPDATE_ANY, "The current user does not have permissions to update an existing user");
            userDao.update(id, request.getPermissions());
            return new CreateUserResponse(id, PerformedActionType.UPDATED);
        }
    }

    @Override
    @Validate
    public UserEntry findByUsername(String username) {
        UUID id = userDao.getId(username);
        if (id == null) {
            throw new WebApplicationException("User not found: " + username, Status.NOT_FOUND);
        }
        return userDao.get(id);
    }

    @Override
    @RequiresPermissions(Permissions.USER_DELETE_ANY)
    public DeleteUserResponse delete(UUID id) {
        if (!userDao.existsById(id)) {
            throw new ValidationErrorsException("User not found: " + id);
        }

        userDao.delete(id);
        return new DeleteUserResponse();
    }

    private void assertPermissions(String wildcard, String perm) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(wildcard)) {
            throw new UnauthorizedException(perm);
        }
    }
}
