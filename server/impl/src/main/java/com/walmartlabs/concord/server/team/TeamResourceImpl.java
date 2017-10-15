package com.walmartlabs.concord.server.team;

import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.team.*;
import com.walmartlabs.concord.server.security.ldap.LdapInfo;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Named
public class TeamResourceImpl implements TeamResource, Resource {

    private final TeamDao teamDao;
    private final UserDao userDao;
    private final LdapManager ldapManager;

    @Inject
    public TeamResourceImpl(TeamDao teamDao, UserDao userDao, LdapManager ldapManager) {
        this.teamDao = teamDao;
        this.userDao = userDao;
        this.ldapManager = ldapManager;
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.TEAM_MANAGE)
    public CreateTeamResponse createOrUpdate(TeamEntry entry) {
        UUID teamId = teamDao.getId(entry.getName());

        if (teamId != null) {
            teamDao.update(teamId, entry.getName(), entry.getDescription());
            return new CreateTeamResponse(OperationResult.UPDATED, teamId);
        } else {
            teamId = teamDao.insert(entry.getName(), entry.getDescription(), true);
            return new CreateTeamResponse(OperationResult.CREATED, teamId);
        }
    }

    @Override
    @RequiresAuthentication
    public TeamEntry get(String teamName) {
        TeamEntry entry = teamDao.getByName(teamName);
        if (entry == null) {
            throw new WebApplicationException("Team not found: " + teamName, Status.NOT_FOUND);
        }
        return entry;
    }

    @Override
    @RequiresAuthentication
    public List<TeamEntry> list() {
        return teamDao.list();
    }

    @Override
    @RequiresAuthentication
    public List<TeamUserEntry> listUsers(String teamName) {
        UUID teamId = assertTeam(teamName);

        return teamDao.listUsers(teamId);
    }

    @Override
    @RequiresPermissions(Permissions.TEAM_MANAGE)
    public AddTeamUsersResponse addUsers(String teamName, Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        UUID teamId = assertTeam(teamName);

        Collection<UUID> userIds = usernames.stream()
                .map(this::getOrCreateUserId)
                .collect(Collectors.toSet());

        teamDao.addUsers(teamId, userIds);

        return new AddTeamUsersResponse();
    }

    @Override
    public RemoveTeamUsersResponse removeUsers(String teamName, Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        UUID teamId = assertTeam(teamName);

        Collection<UUID> userIds = usernames.stream()
                .map(this::getUserId)
                .flatMap(id -> id.map(Stream::of).orElseGet(Stream::empty))
                .collect(Collectors.toSet());

        teamDao.removeUsers(teamId, userIds);

        return new RemoveTeamUsersResponse();
    }

    private UUID assertTeam(String teamName) {
        UUID teamId = teamDao.getId(teamName);
        if (teamId == null) {
            throw new WebApplicationException("Team not found: " + teamName, Status.NOT_FOUND);
        }
        return teamId;
    }

    private UUID getOrCreateUserId(String username) {
        UUID userId = userDao.getId(username);

        if (userId == null) {
            try {
                LdapInfo i = ldapManager.getInfo(username);
                if (i == null) {
                    throw new WebApplicationException("User not found: " + username);
                }

                userId = UUID.randomUUID();
                userDao.insert(userId, username, null);
            } catch (NamingException e) {
                throw new WebApplicationException("Error while retrieving LDAP data: " + e.getMessage(), e);
            }
        }

        return userId;
    }

    private Optional<UUID> getUserId(String username) {
        UUID userId = userDao.getId(username);
        return Optional.ofNullable(userId);
    }
}
