package com.walmartlabs.concord.server.user;

import java.util.Set;
import java.util.UUID;

public abstract class AbstractUserInfoProvider implements UserInfoProvider{
    
    private final UserDao userDao;
    
    public AbstractUserInfoProvider(UserDao userDao){
        this.userDao = userDao;
    }
    
    protected UserInfo getInfo(UUID id, String username, String userDomain, UserType type) {
        if (id == null) {
            id = userDao.getId(username, userDomain, type);
        }

        if (id == null) {
            return null;
        }

        UserEntry e = userDao.get(id);
        if (e == null) {
            return null;
        }

        return UserInfo.builder()
                .id(id)
                .username(e.getName())
                .userDomain(userDomain)
                .displayName(e.getDisplayName())
                .email(e.getEmail())
                .build();
    }

    protected UUID create(String username, String domain, String displayName, String email, Set<String> roles, UserType type) {
        return userDao.insertOrUpdate(username, domain, displayName, email, type, roles);
    }
}
