package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.api.team.TeamRole;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.team.TeamDao;
import com.walmartlabs.concord.server.team.TeamManager;

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
        teamDao.addUser(TeamManager.DEFAULT_TEAM_ID, id, TeamRole.WRITER);
        return userDao.get(id);
    }
}
