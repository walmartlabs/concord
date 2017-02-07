package com.walmartlabs.concord.server.security.apikey;

import com.walmartlabs.concord.server.security.User;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;

@Named
public class ApiKeyRealm extends AuthorizingRealm {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRealm.class);

    private final UserDao userDao;

    @Inject
    public ApiKeyRealm(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof ApiKey;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        ApiKey t = (ApiKey) token;
        User u = userDao.findById(t.getUserId());
        if (u == null) {
            return null;
        }

        // TODO roles?
        log.debug("doGetAuthenticationInfo ['{}'] -> using {}", token, u);
        return new SimpleAccount(Arrays.asList(u, t), t.getKey(), getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        SimpleAuthorizationInfo i = new SimpleAuthorizationInfo();

        User u = (User) principals.getPrimaryPrincipal();
        log.debug("doGetAuthorizationInfo -> using {}", u);

        if (u.getPermissions() != null) {
            for (String p : u.getPermissions()) {
                i.addStringPermission(p);
            }
        }

        return i;
    }
}
