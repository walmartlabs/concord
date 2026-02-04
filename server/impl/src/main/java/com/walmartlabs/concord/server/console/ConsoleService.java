package com.walmartlabs.concord.server.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.cfg.ConsoleConfiguration;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreAccessManager;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreDao;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreEntry;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreQueryDao;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.secret.PasswordChecker;
import com.walmartlabs.concord.server.org.secret.SecretDao;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.repository.InvalidRepositoryPathException;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.security.SecurityUtils;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import com.walmartlabs.concord.server.security.ldap.LdapGroupSearchResult;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.RoleEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;

@Path("/api/service/console")
public class ConsoleService implements Resource {

    private final ProjectDao projectDao;
    private final RepositoryManager repositoryManager;
    private final UserManager userManager;
    private final SecretDao secretDao;
    private final OrganizationManager orgManager;
    private final RepositoryDao repositoryDao;
    private final TeamDao teamDao;
    private final ApiKeyDao apiKeyDao;
    private final ProjectAccessManager projectAccessManager;
    private final JsonStoreDao storageDao;
    private final JsonStoreQueryDao storageQueryDao;
    private final JsonStoreAccessManager jsonStoreAccessManager;
    private final ConsoleServiceDao consoleServiceDao;
    private final ConsoleConfiguration consoleCfg;

    @Inject
    public ConsoleService(ProjectDao projectDao,
                          RepositoryManager repositoryManager,
                          UserManager userManager,
                          SecretDao secretDao,
                          OrganizationManager orgManager,
                          RepositoryDao repositoryDao,
                          TeamDao teamDao,
                          ApiKeyDao apiKeyDao,
                          ProjectAccessManager projectAccessManager,
                          JsonStoreDao storageDao,
                          JsonStoreQueryDao storageQueryDao,
                          JsonStoreAccessManager jsonStoreAccessManager,
                          ConsoleServiceDao consoleServiceDao,
                          ConsoleConfiguration consoleCfg) {

        this.projectDao = projectDao;
        this.repositoryManager = repositoryManager;
        this.userManager = userManager;
        this.repositoryDao = repositoryDao;
        this.secretDao = secretDao;
        this.orgManager = orgManager;
        this.teamDao = teamDao;
        this.apiKeyDao = apiKeyDao;
        this.projectAccessManager = projectAccessManager;
        this.storageDao = storageDao;
        this.storageQueryDao = storageQueryDao;
        this.jsonStoreAccessManager = jsonStoreAccessManager;
        this.consoleServiceDao = consoleServiceDao;
        this.consoleCfg = consoleCfg;
    }

    @GET
    @Path("/cfg")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getCfg() {
        if (consoleCfg.isUseDefaultCfg()) {
            return Response.seeOther(URI.create("/cfg.js")).build();
        }
        return Response.ok(consoleCfg.getCfg()).build();
    }

    @GET
    @Path("/userInfo")
    @Produces(MediaType.APPLICATION_JSON)
    public UserInfoResponse getUserInfo() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (p == null) {
            throw new ConcordApplicationException("Can't determine current user: principal not found",
                    Status.INTERNAL_SERVER_ERROR);
        }

        UserEntry u = p.getUser();
        if (u == null) {
            throw new ConcordApplicationException("Can't determine current user: user entry not found",
                    Status.INTERNAL_SERVER_ERROR);
        }

        Set<String> userLdapGroups = Optional.ofNullable(LdapPrincipal.getCurrent())
                .map(LdapPrincipal::getGroups)
                .orElse(Set.of());

        return UserInfoResponse.builder()
                .id(u.getId())
                .displayName(displayName(u, p))
                .teams(consoleServiceDao.listTeams(p.getId()))
                .roles(u.getRoles().stream().map(RoleEntry::getName).toList())
                .ldapGroups(userLdapGroups)
                .build();
    }

    @GET
    @Path("/whoami")
    @Produces(MediaType.APPLICATION_JSON)
    public UserResponse whoami() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (p == null) {
            throw new ConcordApplicationException("Can't determine current user: principal not found",
                    Status.INTERNAL_SERVER_ERROR);
        }

        UserEntry u = p.getUser();
        if (u == null) {
            throw new ConcordApplicationException("Can't determine current user: user entry not found",
                    Status.INTERNAL_SERVER_ERROR);
        }

        UserEntry user = userManager.get(p.getId())
                .orElseThrow(() -> new ConcordApplicationException("Unknown user: " + p.getId()));

        if (user.isDisabled()) {
            throw new UnauthorizedException("User is disabled: " + p.getId());
        }

        return new UserResponse(p.getRealm(), user.getName(), user.getDomain(), displayName(u, p), user.getOrgs());
    }

    @POST
    @Path("/logout")
    public void logout() {
        SecurityUtils.logout();
    }

    @GET
    @Path("/org/{orgName}/project/{projectName}/exists")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public boolean isProjectExists(@PathParam("orgName") @ConcordKey String orgName,
                                   @PathParam("projectName") String projectName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        return projectDao.getId(org.getId(), projectName) != null;
    }

    @GET
    @Path("/org/{orgName}/jsonstore/{storageName}/exists")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public boolean isStorageExists(@PathParam("orgName") @ConcordKey String orgName,
                                   @PathParam("storageName") String storageName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        return storageDao.getId(org.getId(), storageName) != null;
    }

    @GET
    @Path("/org/{orgName}/jsonstore/{storeName}/query/{queryName}/exists")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public boolean isStorageQueryExists(@PathParam("orgName") @ConcordKey String orgName,
                                        @PathParam("storeName") String storeName,
                                        @PathParam("queryName") String queryName) {

        try {
            OrganizationEntry org = orgManager.assertAccess(orgName, true);
            JsonStoreEntry storage = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);
            return storageQueryDao.getId(storage.id(), queryName) != null;
        } catch (UnauthorizedException e) {
            return false;
        }
    }

    @GET
    @Path("/org/{orgName}/secret/{secretName}/exists")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public boolean isSecretExists(@PathParam("orgName") @ConcordKey String orgName,
                                  @PathParam("secretName") String secretName) {
        try {
            OrganizationEntry org = orgManager.assertAccess(orgName, true);
            return secretDao.getId(org.getId(), secretName) != null;
        } catch (UnauthorizedException e) {
            return false;
        }
    }

    @GET
    @Path("/org/{orgName}/project/{projectName}/repo/{repoName}/exists")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public boolean isRepositoryExists(@PathParam("orgName") @ConcordKey String orgName,
                                      @PathParam("projectName") @ConcordKey String projectName,
                                      @PathParam("repoName") String repoName) {
        try {
            OrganizationEntry org = orgManager.assertAccess(orgName, true);
            UUID projectId = projectDao.getId(org.getId(), projectName);
            if (projectId == null) {
                throw new ConcordApplicationException("Project not found: " + projectName, Status.BAD_REQUEST);
            }
            return repositoryDao.getId(projectId, repoName) != null;
        } catch (UnauthorizedException e) {
            return false;
        }
    }

    @GET
    @Path("/org/{orgName}/team/{teamName}/exists")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public boolean isTeamExists(@PathParam("orgName") @ConcordKey String orgName,
                                @PathParam("teamName") @ConcordKey String teamName) {
        try {
            OrganizationEntry org = orgManager.assertAccess(orgName, true);
            return teamDao.getId(org.getId(), teamName) != null;
        } catch (UnauthorizedException e) {
            return false;
        }
    }

    @GET
    @Path("/apikey/{name}/exists")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public boolean isApiTokenExists(@PathParam("name") @ConcordKey String tokenName) {
        UserPrincipal currentUser = UserPrincipal.getCurrent();
        if (currentUser == null) {
            return false;
        }

        UUID userId = currentUser.getId();
        return apiKeyDao.getId(userId, tokenName) != null;
    }

    @POST
    @Path("/repository/test")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public boolean testRepository(RepositoryTestRequest req) {
        OrganizationEntry org = orgManager.assertAccess(null, req.getOrgName(), false);
        ProjectEntry project = projectAccessManager.assertAccess(org.getId(), null, req.getProjectName(), ResourceAccessLevel.READER, false);

        try {
            String secretName = secretDao.getName(req.getSecretId());
            repositoryManager.testConnection(project.getOrgId(), project.getId(), req.getUrl(), req.getBranch(), req.getCommitId(), req.getPath(), secretName);
            return true;
        } catch (InvalidRepositoryPathException e) {
            Map<String, String> m = new HashMap<>();
            m.put("message", "Repository validation error");
            m.put("level", "WARN");
            m.put("details", e.getMessage());

            throw new ConcordApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .entity(m)
                    .build());
        } catch (Exception e) {
            String msg;
            Throwable t = e;
            while (true) {
                msg = t.getMessage();
                t = t.getCause();
                if (t == null) {
                    break;
                }
            }

            if (msg == null) {
                msg = "Repository test error";
            }

            throw new ConcordApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                    .entity(msg)
                    .build());
        }
    }

    @GET
    @Path("/search/ldapGroups")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @WithTimer
    public List<LdapGroupSearchResult> searchLdapGroups(@QueryParam("filter") @Size(min = 5, max = 256) String filter) {
        if (filter == null) {
            return Collections.emptyList();
        }

        filter = filter.trim();
        if (filter.startsWith("*")) {
            // disallow "starts-with" filters, they can be too slow
            return Collections.emptyList();
        }

        return userManager.searchLdapGroups(filter);
    }

    @POST
    @Path("/validate-password")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public boolean validatePassword(String pwd) {
        try {
            PasswordChecker.check(pwd);
        } catch (PasswordChecker.CheckerException e) {
            return false;
        }

        return true;
    }

    private static String displayName(UserEntry u, UserPrincipal p) {
        String displayName = u.getDisplayName();

        if (displayName == null) {
            LdapPrincipal l = LdapPrincipal.getCurrent();
            if (l != null) {
                displayName = l.getDisplayName();
            }
        }

        if (displayName == null) {
            displayName = p.getUsername();
        }

        return displayName;
    }

    public static class ConsoleServiceDao extends AbstractDao {

        @Inject
        public ConsoleServiceDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        @Override
        protected <T> T txResult(TxResult<T> t) {
            return super.txResult(t);
        }

        public List<UserInfoResponse.UserTeamInfo> listTeams(UUID userId) {
            return txResult(tx -> tx.select(ORGANIZATIONS.ORG_NAME, TEAMS.TEAM_NAME, USER_TEAMS.TEAM_ROLE)
                    .from(USER_TEAMS)
                    .join(TEAMS).on(TEAMS.TEAM_ID.eq(USER_TEAMS.TEAM_ID))
                    .join(ORGANIZATIONS).on(ORGANIZATIONS.ORG_ID.eq(TEAMS.ORG_ID))
                    .where(USER_TEAMS.USER_ID.eq(userId))
                    .fetch(r -> UserInfoResponse.UserTeamInfo.of(r.get(ORGANIZATIONS.ORG_NAME), r.get(TEAMS.TEAM_NAME), TeamRole.valueOf(r.get(USER_TEAMS.TEAM_ROLE)))));
        }
    }
}
