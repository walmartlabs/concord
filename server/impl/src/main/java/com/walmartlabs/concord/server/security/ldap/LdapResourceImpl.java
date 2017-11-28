package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.api.PerformedActionType;
import com.walmartlabs.concord.server.api.security.ldap.*;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.RoleDao;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.NamingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.util.*;

@Named
@Singleton
public class LdapResourceImpl implements LdapResource, Resource {

    private final LdapDao ldapDao;
    private final RoleDao roleDao;
    private final LdapManager ldapManager;

    @Inject
    public LdapResourceImpl(LdapDao ldapDao, RoleDao roleDao, LdapManager ldapManager) {
        this.ldapDao = ldapDao;
        this.roleDao = roleDao;
        this.ldapManager = ldapManager;
    }

    @Override
    @Validate
    public CreateLdapMappingResponse createOrUpdate(CreateLdapMappingRequest request) {
        assertAdmin();

        String ldapDn = request.getLdapDn();
        validateRoles(request.getRoles());

        UUID id = ldapDao.getId(ldapDn);
        if (id != null) {
            ldapDao.update(id, ldapDn, request.getRoles());
            return new CreateLdapMappingResponse(id, PerformedActionType.UPDATED);
        } else {
            id = UUID.randomUUID();
            ldapDao.insert(id, ldapDn, request.getRoles());
            return new CreateLdapMappingResponse(id, PerformedActionType.CREATED);
        }
    }

    @Override
    public List<LdapMappingEntry> listMappings() {
        return ldapDao.list();
    }

    @Override
    @Validate
    public DeleteLdapMappingResponse deleteMapping(UUID id) {
        assertAdmin();

        if (!ldapDao.exists(id)) {
            throw new ValidationErrorsException("LDAP group mapping not found: " + id);
        }

        ldapDao.delete(id);
        return new DeleteLdapMappingResponse();
    }

    @Override
    @Validate
    public List<String> getLdapGroups(String username) {
        assertAdmin();

        try {
            LdapInfo i = ldapManager.getInfo(username);

            List<String> l = new ArrayList<>(i.getGroups());
            Collections.sort(l);
            return l;
        } catch (NamingException e) {
            throw new WebApplicationException("LDAP query error", e);
        }
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

    private static void assertAdmin() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (!p.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }
}
