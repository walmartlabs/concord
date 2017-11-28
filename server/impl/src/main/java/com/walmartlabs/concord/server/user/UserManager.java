package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.api.org.team.TeamRole;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamManager;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Named
public class UserManager {

    private final UserDao userDao;
    private final TeamDao teamDao;

    @Inject
    public UserManager(UserDao userDao, TeamDao teamDao) {
        this.userDao = userDao;
        this.teamDao = teamDao;
    }

    public UserEntry getOrCreate(String username) {
        return getOrCreate(username, null);
    }

    public UserEntry getOrCreate(String username, Set<String> permissions) {
        UUID id = userDao.getId(username);
        if (id == null) {
            return create(username, permissions);
        }
        return userDao.get(id);
    }

    public Optional<UserEntry> get(UUID id) {
        return Optional.of(userDao.get(id));
    }

    public Optional<UUID> getId(String username) {
        UUID id = userDao.getId(username);
        return Optional.ofNullable(id);
    }

    public UserEntry create(String username, Set<String> permissions) {
        UUID id = userDao.insert(username, permissions);

        // TODO teams
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        teamDao.addUser(teamId, id, TeamRole.MEMBER);

        return userDao.get(id);
    }

    public boolean isInOrganization(UUID orgId) {
        UserPrincipal p = UserPrincipal.getCurrent();
        UUID userId = p.getId();
        return userDao.isInOrganization(userId, orgId);
    }
}
