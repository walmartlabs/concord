package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.user.CreateRoleResponse;
import com.walmartlabs.concord.server.api.user.DeleteRoleResponse;
import com.walmartlabs.concord.server.api.user.RoleEntry;
import com.walmartlabs.concord.server.api.user.RoleResource;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class RoleResourceImpl implements RoleResource, Resource {

    private final RoleDao roleDao;

    @Inject
    public RoleResourceImpl(RoleDao roleDao) {
        this.roleDao = roleDao;
    }

    @Override
    @Validate
    public CreateRoleResponse createOrUpdate(RoleEntry entry) {
        String roleName = entry.getName();

        if (roleDao.exists(roleName)) {
            assertPermissions(Permissions.ROLE_UPDATE_ANY,
                    "The current user does not have permissions to update the specified role");

            roleDao.update(roleName, entry.getDescription(), entry.getPermissions());
            return new CreateRoleResponse(false);
        } else {
            assertPermissions(Permissions.ROLE_CREATE_NEW,
                    "The current user does not have permissions to create a new role");

            roleDao.insert(roleName, entry.getDescription(), entry.getPermissions());
            return new CreateRoleResponse(true);
        }
    }

    @Override
    public List<RoleEntry> get() {
        return roleDao.list();
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.ROLE_DELETE_ANY)
    public DeleteRoleResponse delete(String name) {
        if (!roleDao.exists(name)) {
            throw new ValidationErrorsException("Role not found: " + name);
        }

        roleDao.delete(name);
        return new DeleteRoleResponse();
    }

    private static void assertPermissions(String permission, String message) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(permission)) {
            throw new UnauthorizedException(message);
        }
    }
}
