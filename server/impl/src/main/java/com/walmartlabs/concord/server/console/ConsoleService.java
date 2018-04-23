package com.walmartlabs.concord.server.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.secret.SecretDao;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.subject.Subject;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.UUID;

@Named
@Path("/api/service/console")
public class ConsoleService implements Resource {

    private final ProjectDao projectDao;
    private final RepositoryManager repositoryManager;
    private final UserManager userManager;
    private final SecretDao secretDao;
    private final OrganizationManager orgManager;
    private final RepositoryDao repositoryDao;


    @Inject
    public ConsoleService(ProjectDao projectDao,
                          RepositoryManager repositoryManager,
                          UserManager userManager,
                          SecretDao secretDao,
                          OrganizationManager orgManager,
                          RepositoryDao repositoryDao) {

        this.projectDao = projectDao;
        this.repositoryManager = repositoryManager;
        this.userManager = userManager;
        this.repositoryDao = repositoryDao;
        this.secretDao = secretDao;
        this.orgManager = orgManager;
    }

    @GET
    @Path("/whoami")
    @Produces(MediaType.APPLICATION_JSON)
    public UserResponse whoami() {
        UserPrincipal p = UserPrincipal.getCurrent();
        if (p == null) {
            throw new WebApplicationException("Can't determine current user: entry not found",
                    Status.INTERNAL_SERVER_ERROR);
        }

        String displayName = null;

        LdapPrincipal l = LdapPrincipal.getCurrent();
        if (l != null) {
            displayName = l.getDisplayName();
        }

        if (displayName == null) {
            displayName = p.getUsername();
        }

        UserEntry user = userManager.get(p.getId())
                .orElseThrow(() -> new WebApplicationException("Unknown user: " + p.getId()));

        return new UserResponse(p.getRealm(), user.getName(), displayName, user.getOrgs());
    }

    @POST
    @Path("/logout")
    public void logout() {
        Subject subject = SecurityUtils.getSubject();
        if (subject == null || !subject.isAuthenticated()) {
            return;
        }

        subject.logout();
    }

    @GET
    @Path("/org/{orgName}/project/{projectName}/exists")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean isProjectExists(@PathParam("orgName") @ConcordKey String orgName,
                                   @PathParam("projectName") String projectName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        return projectDao.getId(org.getId(), projectName) != null;
    }

    @GET
    @Path("/org/{orgName}/secret/{secretName}/exists")
    @Produces(MediaType.APPLICATION_JSON)
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
    public boolean isRepositoryExists(@PathParam("orgName") @ConcordKey String orgName,
                                      @PathParam("projectName") @ConcordKey String projectName,
                                      @PathParam("repoName") String repoName) {
        try {
            OrganizationEntry org = orgManager.assertAccess(orgName, true);
            UUID projectId = projectDao.getId(org.getId(), projectName);
            if (projectId == null) {
                throw new WebApplicationException("Project not found: " + projectName, Status.BAD_REQUEST);
            }
            return repositoryDao.getId(projectId, repoName) != null;
        } catch (UnauthorizedException e) {
            return false;
        }
    }

    @POST
    @Path("/repository/test")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public boolean testRepository(RepositoryTestRequest req) {
        OrganizationEntry org = orgManager.assertAccess(req.getOrgId(), req.getOrgName(), false);

        try {
            repositoryManager.testConnection(org.getId(), req.getUrl(), req.getBranch(), req.getCommitId(), req.getPath(), req.getSecret());
            return true;
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

            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                    .entity(msg)
                    .build());
        }
    }
}
