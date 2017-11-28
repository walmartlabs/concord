package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.api.PerformedActionType;
import com.walmartlabs.concord.server.api.user.CreateRoleResponse;
import com.walmartlabs.concord.server.api.user.DeleteRoleResponse;
import com.walmartlabs.concord.server.api.user.RoleEntry;
import com.walmartlabs.concord.server.api.user.RoleResource;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
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
        assertAdmin();

        String roleName = entry.getName();

        if (roleDao.exists(roleName)) {
            roleDao.update(roleName, entry.getDescription(), entry.getPermissions());
            return new CreateRoleResponse(PerformedActionType.UPDATED);
        } else {
            roleDao.insert(roleName, entry.getDescription(), entry.getPermissions());
            return new CreateRoleResponse(PerformedActionType.CREATED);
        }
    }

    @Override
    public List<RoleEntry> get() {
        return roleDao.list();
    }

    @Override
    @Validate
    public DeleteRoleResponse delete(String name) {
        assertAdmin();

        if (!roleDao.exists(name)) {
            throw new ValidationErrorsException("Role not found: " + name);
        }

        roleDao.delete(name);
        return new DeleteRoleResponse();
    }

    private static void assertAdmin() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (!p.isAdmin()) {
            throw new WebApplicationException("Only admins can do that", Response.Status.FORBIDDEN);
        }
    }
}
