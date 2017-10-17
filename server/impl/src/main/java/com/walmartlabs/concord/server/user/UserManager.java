package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.api.team.TeamEntry;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.team.TeamDao;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

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
        return getOrCreate(username, null, null);
    }

    public UserEntry getOrCreate(String username, Set<String> teamNames, Set<String> permissions) {
        UUID id = userDao.getId(username);
        if (id == null) {
            return create(username, teamNames, permissions);
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
        return create(username, null, permissions);
    }

    public UserEntry create(String username, Set<String> teamNames, Set<String> permissions) {
        if (teamNames == null) {
            TeamEntry team = teamDao.get(TeamDao.DEFAULT_TEAM_ID);
            if (team == null) {
                throw new ValidationErrorsException("Can't find the default team");
            }

            teamNames = Collections.singleton(team.getName());
        }

        UUID id = userDao.insert(username, permissions);

        Collection<UUID> uIds = Collections.singleton(id);
        for (String tName : teamNames) {
            UUID tId = teamDao.getId(tName);
            if (tId == null) {
                throw new ValidationErrorsException("Unknown team: " + tName);
            }

            teamDao.addUsers(tId, uIds);
        }

        return userDao.get(id);
    }
}
