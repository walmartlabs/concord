package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.user.RoleDao;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;

@Named
public class ConcordShiroAuthorizer {

    private final UserManager userManager;
    private final RoleDao roleDao;

    @Inject
    public ConcordShiroAuthorizer(UserManager userManager, RoleDao roleDao) {
        this.userManager = userManager;
        this.roleDao = roleDao;
    }

    public AuthorizationInfo getAuthorizationInfo(UserPrincipal p, Collection<String> roles) {
        SimpleAuthorizationInfo i = new SimpleAuthorizationInfo();

        UserEntry u = userManager.get(p.getId())
                .orElse(null);

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
