package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.user.RoleDao;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;

@Named
public class ConcordShiroAuthorizer {

    private final RoleDao roleDao;

    @Inject
    public ConcordShiroAuthorizer(RoleDao roleDao) {
        this.roleDao = roleDao;
    }

    public AuthorizationInfo getAuthorizationInfo(UserEntry u, Collection<String> roles) {
        SimpleAuthorizationInfo i = new SimpleAuthorizationInfo();

        if (u.getPermissions() != null) {
            i.addStringPermissions(u.getPermissions());
        }

        if (roles != null) {
            Collection<String> permissions = roleDao.getPermissions(roles);
            i.addStringPermissions(permissions);
        }

        return i;
    }
}
