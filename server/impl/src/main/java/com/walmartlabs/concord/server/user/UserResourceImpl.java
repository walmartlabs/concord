package com.walmartlabs.concord.server.user;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.user.*;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.authz.UnauthorizedException;
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
        assertAdmin();

        String username = request.getUsername();

        UUID id = userDao.getId(username);
        if (id == null) {
            boolean admin = request.getAdmin() != null ? request.getAdmin() : false;
            UserEntry e = userManager.create(username, request.getPermissions(), admin);
            return new CreateUserResponse(e.getId(), OperationResult.CREATED);
        } else {
            userDao.update(id, request.getPermissions(), request.getAdmin());
            return new CreateUserResponse(id, OperationResult.UPDATED);
        }
    }

    @Override
    @Validate
    public UserEntry findByUsername(String username) {
        assertAdmin();

        UUID id = userDao.getId(username);
        if (id == null) {
            throw new WebApplicationException("User not found: " + username, Status.NOT_FOUND);
        }
        return userDao.get(id);
    }

    @Override
    public DeleteUserResponse delete(UUID id) {
        assertAdmin();

        if (!userDao.existsById(id)) {
            throw new ValidationErrorsException("User not found: " + id);
        }

        userDao.delete(id);
        return new DeleteUserResponse();
    }

    private static void assertAdmin() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (!p.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }
}
