package com.walmartlabs.concord.server.team;

import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.team.*;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapInfo;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Named
public class TeamResourceImpl implements TeamResource, Resource {

    private final TeamDao teamDao;
    private final TeamManager teamManager;
    private final UserManager userManager;
    private final LdapManager ldapManager;

    @Inject
    public TeamResourceImpl(TeamDao teamDao, TeamManager teamManager, UserManager userManager, LdapManager ldapManager) {
        this.teamDao = teamDao;
        this.teamManager = teamManager;
        this.userManager = userManager;
        this.ldapManager = ldapManager;
    }

    @Override
    @Validate
    public CreateTeamResponse createOrUpdate(TeamEntry entry) {
        UUID teamId = teamDao.getId(entry.getName());

        if (teamId != null) {
            teamManager.assertTeamAccess(teamId, TeamRole.OWNER, true);
            teamDao.update(teamId, entry.getName(), entry.getDescription());
            return new CreateTeamResponse(OperationResult.UPDATED, teamId);
        } else {
            assertIsAdmin();

            TeamVisibility visibility = entry.getVisibility();
            if (visibility == null) {
                visibility = TeamVisibility.PUBLIC;
            }

            teamId = teamDao.insert(entry.getName(), entry.getDescription(), true, visibility);
            return new CreateTeamResponse(OperationResult.CREATED, teamId);
        }
    }

    @Override
    @RequiresAuthentication
    public TeamEntry get(String teamName) {
        return assertTeam(teamName, TeamRole.READER, false);
    }

    @Override
    @RequiresAuthentication
    public List<TeamEntry> list() {
        return teamManager.list();
    }

    @Override
    @RequiresAuthentication
    public List<TeamUserEntry> listUsers(String teamName) {
        TeamEntry t = assertTeam(teamName, TeamRole.READER, false);
        return teamDao.listUsers(t.getId());
    }

    @Override
    public AddTeamUsersResponse addUsers(String teamName, Collection<TeamUserEntry> users) {
        if (users == null || users.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        TeamEntry t = assertTeam(teamName, TeamRole.OWNER, true);

        teamDao.tx(tx -> {
            for (TeamUserEntry u : users) {
                UUID userId = getOrCreateUserId(u.getUsername());

                TeamRole role = u.getRole();
                if (role == null) {
                    role = TeamRole.READER;
                }

                teamDao.addUsers(tx, t.getId(), userId, role);
            }
        });

        return new AddTeamUsersResponse();
    }

    @Override
    public RemoveTeamUsersResponse removeUsers(String teamName, Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        TeamEntry t = assertTeam(teamName, TeamRole.OWNER, true);

        Collection<UUID> userIds = usernames.stream()
                .map(userManager::getId)
                .flatMap(id -> id.map(Stream::of).orElseGet(Stream::empty))
                .collect(Collectors.toSet());

        teamDao.removeUsers(t.getId(), userIds);

        return new RemoveTeamUsersResponse();
    }

    private TeamEntry assertTeam(String teamName, TeamRole requiredRole, boolean teamMembersOnly) {
        UUID id = teamDao.getId(teamName);
        if (id == null) {
            throw new WebApplicationException("Team not found: " + teamName, Status.NOT_FOUND);
        }

        return teamManager.assertTeamAccess(id, requiredRole, teamMembersOnly);
    }

    private void assertIsAdmin() {
        if (!UserPrincipal.getCurrent().isAdmin()) {
            throw new UnauthorizedException("The current user is not an administrator");
        }
    }

    private UUID getOrCreateUserId(String username) {
        UserEntry user = userManager.getOrCreate(username);

        if (user == null) {
            try {
                LdapInfo i = ldapManager.getInfo(username);
                if (i == null) {
                    throw new WebApplicationException("User not found: " + username);
                }
            } catch (NamingException e) {
                throw new WebApplicationException("Error while retrieving LDAP data: " + e.getMessage(), e);
            }

            user = userManager.getOrCreate(username);
        }

        return user.getId();
    }
}
