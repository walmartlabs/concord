package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.security.ldap.*;
import com.walmartlabs.concord.server.user.RoleDao;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Named
public class LdapResourceImpl implements LdapResource, Resource {

    private final LdapDao ldapDao;
    private final RoleDao roleDao;

    @Inject
    public LdapResourceImpl(LdapDao ldapDao, RoleDao roleDao) {
        this.ldapDao = ldapDao;
        this.roleDao = roleDao;
    }

    @Override
    @Validate
    public CreateLdapMappingResponse createOrUpdate(CreateLdapMappingRequest request) {
        String ldapDn = request.getLdapDn();
        validateRoles(request.getRoles());

        String id = ldapDao.getId(ldapDn);
        if (id != null) {
            assertPermissions(Permissions.LDAP_MAPPING_UPDATE_ANY,
                    "The current user does not have permissions to update the specified LDAP group mapping");

            ldapDao.update(id, ldapDn, request.getRoles());
            return new CreateLdapMappingResponse(id, false);
        } else {
            assertPermissions(Permissions.LDAP_MAPPING_CREATE_NEW,
                    "The current user does not have permissions to create a new LDAP group mapping");

            id = UUID.randomUUID().toString();
            ldapDao.insert(id, ldapDn, request.getRoles());
            return new CreateLdapMappingResponse(id, true);
        }
    }

    @Override
    public List<LdapMappingEntry> list() {
        return ldapDao.list();
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.LDAP_MAPPING_DELETE_ANY)
    public DeleteLdapMappingResponse delete(String id) {
        if (!ldapDao.exists(id)) {
            throw new ValidationErrorsException("LDAP group mapping not found: " + id);
        }

        ldapDao.delete(id);
        return new DeleteLdapMappingResponse();
    }

    private void validateRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }

        for (String r : roles) {
            if (!roleDao.exists(r)) {
                throw new ValidationErrorsException("Role not found: " + r);
            }
        }
    }

    private static void assertPermissions(String permission, String message) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(permission)) {
            throw new UnauthorizedException(message);
        }
    }
}
