package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.api.user.*;
import com.walmartlabs.concord.server.api.security.Permissions;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

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
        String id = UUID.randomUUID().toString();
        userDao.insert(id, request.getUsername(), request.getPermissions());
        // TODO password?
        return new CreateUserResponse(id);
    }

    @Override
    @RequiresPermissions(Permissions.USER_DELETE_ANY)
    public DeleteUserResponse delete(String id) {
        userDao.delete(id);
        return new DeleteUserResponse();
    }

    @Override
    @RequiresPermissions(Permissions.USER_UPDATE_ANY)
    public UpdateUserResponse update(String id, UpdateUserRequest request) {
        userDao.update(id, request.getPermissions());
        return new UpdateUserResponse();
    }
}
