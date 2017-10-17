package com.walmartlabs.concord.server.security.apikey;

import com.walmartlabs.concord.server.security.ConcordShiroAuthorizer;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;

@Named
public class ApiKeyRealm extends AuthorizingRealm {

    private final UserManager userManager;
    private final ConcordShiroAuthorizer authorizer;

    @Inject
    public ApiKeyRealm(UserManager userManager, ConcordShiroAuthorizer authorizer) {
        this.userManager = userManager;
        this.authorizer = authorizer;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof ApiKey;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        ApiKey t = (ApiKey) token;

        return userManager.get(t.getUserId())
                .map(u -> {
                    // TODO roles?
                    UserPrincipal p = new UserPrincipal("apikey", u.getId(), u.getName(), null);
                    return new SimpleAccount(Arrays.asList(p, t), t.getKey(), getName());
                })
                .orElse(null);
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserPrincipal p = (UserPrincipal) principals.getPrimaryPrincipal();
        if (!"apikey".equals(p.getRealm())) {
            return null;
        }
        return authorizer.getAuthorizationInfo(p, null);
    }
}
