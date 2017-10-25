package com.walmartlabs.concord.server.team;

import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.team.*;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapInfo;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.subject.Subject;
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
    private final UserManager userManager;
    private final LdapManager ldapManager;

    @Inject
    public TeamResourceImpl(TeamDao teamDao, UserManager userManager, LdapManager ldapManager) {
        this.teamDao = teamDao;
        this.userManager = userManager;
        this.ldapManager = ldapManager;
    }

    @Override
    @Validate
    public CreateTeamResponse createOrUpdate(TeamEntry entry) {
        UUID teamId = teamDao.getId(entry.getName());

        if (teamId != null) {
            assertTeamRole(teamId, TeamRole.OWNER);
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
        TeamEntry entry = teamDao.getByName(teamName);
        if (entry == null) {
            throw new WebApplicationException("Team not found: " + teamName, Status.NOT_FOUND);
        }

        if (entry.getVisibility() != TeamVisibility.PUBLIC) {
            assertTeamRole(entry.getId(), TeamRole.READER);
        }

        return entry;
    }

    @Override
    @RequiresAuthentication
    public List<TeamEntry> list() {
        return teamDao.list(getCurrentUserId());
    }

    @Override
    @RequiresAuthentication
    public List<TeamUserEntry> listUsers(String teamName) {
        UUID teamId = assertTeam(teamName, TeamRole.READER, false);
        return teamDao.listUsers(teamId);
    }

    @Override
    public AddTeamUsersResponse addUsers(String teamName, Collection<TeamUserEntry> users) {
        if (users == null || users.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        UUID teamId = assertTeam(teamName, TeamRole.OWNER, true);

        teamDao.tx(tx -> {
            for (TeamUserEntry u : users) {
                UUID userId = getOrCreateUserId(u.getUsername());

                TeamRole role = u.getRole();
                if (role == null) {
                    role = TeamRole.READER;
                }

                teamDao.addUsers(tx, teamId, userId, role);
            }
        });

        return new AddTeamUsersResponse();
    }

    @Override
    public RemoveTeamUsersResponse removeUsers(String teamName, Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            throw new ValidationErrorsException("Empty user list");
        }

        UUID teamId = assertTeam(teamName, TeamRole.OWNER, true);

        Collection<UUID> userIds = usernames.stream()
                .map(userManager::getId)
                .flatMap(id -> id.map(Stream::of).orElseGet(Stream::empty))
                .collect(Collectors.toSet());

        teamDao.removeUsers(teamId, userIds);

        return new RemoveTeamUsersResponse();
    }

    private UUID assertTeam(String teamName, TeamRole role, boolean teamMembersOnly) {
        TeamEntry t = teamDao.getByName(teamName);
        if (t == null) {
            throw new WebApplicationException("Team not found: " + teamName, Status.NOT_FOUND);
        }

        if (!isAdmin() && (teamMembersOnly || t.getVisibility() == TeamVisibility.PRIVATE)) {
            assertTeamRole(t.getId(), role);
        }

        return t.getId();
    }

    private void assertTeamRole(UUID teamId, TeamRole role) {
        UUID userId = getCurrentUserId();
        if (!teamDao.hasUser(teamId, userId, TeamRole.atLeast(role))) {
            throw new UnauthorizedException("The current user does not have access to the specified team (id=" + teamId + ")");
        }
    }

    private void assertIsAdmin() {
        if (!isAdmin()) {
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

    private static UUID getCurrentUserId() {
        Subject subject = SecurityUtils.getSubject();
        UserPrincipal p = (UserPrincipal) subject.getPrincipal();
        return p.getId();
    }

    private static boolean isAdmin() {
        Subject subject = SecurityUtils.getSubject();
        UserPrincipal p = (UserPrincipal) subject.getPrincipal();
        return p.isAdmin();
    }
}
