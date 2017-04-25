package com.walmartlabs.concord.server.security.apikey;

import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.security.ConcordShiroAuthorizer;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
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
    private final ConcordShiroAuthorizer authorizer;

    @Inject
    public ApiKeyRealm(UserDao userDao, ConcordShiroAuthorizer authorizer) {
        this.userDao = userDao;
        this.authorizer = authorizer;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof ApiKey;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        ApiKey t = (ApiKey) token;

        UserEntry u = userDao.get(t.getUserId());
        if (u == null) {
            return null;
        }

        // TODO roles?
        UserPrincipal p = new UserPrincipal("apikey", t.getUserId(), u.getName());
        return new SimpleAccount(Arrays.asList(p, t), t.getKey(), getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserPrincipal p = (UserPrincipal) principals.getPrimaryPrincipal();
        return authorizer.getAuthorizationInfo(p, null);
    }
}
