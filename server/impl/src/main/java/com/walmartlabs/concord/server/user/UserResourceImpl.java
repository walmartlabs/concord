package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.user.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class UserResourceImpl implements UserResource, Resource {

    private final UserDao userDao;

    @Inject
    public UserResourceImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.USER_CREATE_NEW)
    public CreateUserResponse create(CreateUserRequest request) {
        if (userDao.exists(request.getUsername())) {
            throw new ValidationErrorsException("The user already exists: " + request.getUsername());
        }

        String id = UUID.randomUUID().toString();
        userDao.insert(id, request.getUsername(), request.getPermissions());
        // TODO password?
        return new CreateUserResponse(id);
    }

    @Override
    @RequiresPermissions(Permissions.USER_DELETE_ANY)
    public DeleteUserResponse delete(String id) {
        if (!userDao.existsById(id)) {
            throw new ValidationErrorsException("User not found: " + id);
        }

        userDao.delete(id);
        return new DeleteUserResponse();
    }

    @Override
    @RequiresPermissions(Permissions.USER_UPDATE_ANY)
    public UpdateUserResponse update(String id, UpdateUserRequest request) {
        if (!userDao.existsById(id)) {
            throw new ValidationErrorsException("User not found: " + id);
        }

        userDao.update(id, request.getPermissions());
        return new UpdateUserResponse();
    }
}
