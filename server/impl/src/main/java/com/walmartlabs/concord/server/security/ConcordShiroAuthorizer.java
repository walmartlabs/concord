package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.user.RoleDao;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;

@Named
public class ConcordShiroAuthorizer {

    private final UserDao userDao;
    private final RoleDao roleDao;

    @Inject
    public ConcordShiroAuthorizer(UserDao userDao, RoleDao roleDao) {
        this.userDao = userDao;
        this.roleDao = roleDao;
    }

    public AuthorizationInfo getAuthorizationInfo(UserPrincipal p, Collection<String> roles) {
        SimpleAuthorizationInfo i = new SimpleAuthorizationInfo();

        UserEntry u = null;
        if (p.getId() != null) {
            u = userDao.get(p.getId());
        }

        if (u != null && u.getPermissions() != null) {
            i.addStringPermissions(u.getPermissions());
        }

        if (roles != null) {
            Collection<String> permissions = roleDao.getPermissions(roles);
            i.addStringPermissions(permissions);
        }

        return i;
    }
}
