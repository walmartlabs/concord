package com.walmartlabs.concord.server.security.github;

import com.walmartlabs.concord.server.security.ConcordShiroAuthorizer;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKey;
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
import java.util.UUID;

@Named
public class GithubRealm extends AuthorizingRealm {

    private static final String REALM_NAME = "github";

    private static final String USER = "github";

    private final UserManager userManager;
    private final ConcordShiroAuthorizer authorizer;

    @Inject
    public GithubRealm(UserManager userManager, ConcordShiroAuthorizer authorizer) {
        this.userManager = userManager;
        this.authorizer = authorizer;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof GithubKey;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        GithubKey t = (GithubKey) token;

        UUID userId = userManager.getId(USER).orElse(null);
        if (userId == null) {
            return null;
        }

        return userManager.get(userId)
                .map(u -> {
                    UserPrincipal p = new UserPrincipal(REALM_NAME, u.getId(), u.getName(), null, u.isAdmin());
                    return new SimpleAccount(Arrays.asList(p, t), t.getKey(), getName());
                })
                .orElse(null);
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserPrincipal p = (UserPrincipal) principals.getPrimaryPrincipal();
        if (!REALM_NAME.equals(p.getRealm())) {
            return null;
        }
        return authorizer.getAuthorizationInfo(p, null);
    }
}
