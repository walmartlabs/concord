package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.api.user.UserEntry;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;

import javax.inject.Named;

@Named
public class ConcordShiroAuthorizer {

    public AuthorizationInfo getAuthorizationInfo(UserEntry u) {
        SimpleAuthorizationInfo i = new SimpleAuthorizationInfo();
        if (u.getPermissions() != null) {
            for (String p : u.getPermissions()) {
                i.addStringPermission(p);
            }
        }
        return i;
    }
}
