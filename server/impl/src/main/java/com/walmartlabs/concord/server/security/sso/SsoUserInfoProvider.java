package com.walmartlabs.concord.server.security.sso;

import com.walmartlabs.concord.server.user.AbstractUserInfoProvider;
import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserType;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Set;
import java.util.UUID;

@Named
@Singleton
public class SsoUserInfoProvider extends AbstractUserInfoProvider {
    
    @Inject
    public SsoUserInfoProvider(UserDao userDao) {
        super(userDao);
    }

    @Override
    public UserType getUserType() {
        return UserType.SSO;
    }

    @Override
    public UserInfo getInfo(UUID id, String username, String userDomain) {
        return getInfo(id, username, userDomain, UserType.LDAP);
    }

    @Override
    public UUID create(String username, String domain, String displayName, String email, Set<String> roles) {
        return create(username, domain, displayName, email, roles, UserType.LDAP);
    }
}
