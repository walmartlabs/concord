package com.walmartlabs.concord.server.console;

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapInfo;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
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
    private final SecretManager secretManager;
    private final OrganizationManager orgManager;

    @Inject
    public ConsoleService(ProjectDao projectDao,
                          RepositoryManager repositoryManager,
                          UserManager userManager,
                          SecretManager secretManager,
                          OrganizationManager orgManager) {

        this.projectDao = projectDao;
        this.repositoryManager = repositoryManager;
        this.userManager = userManager;
        this.secretManager = secretManager;
        this.orgManager = orgManager;
    }

    @GET
    @Path("/whoami")
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    public UserResponse whoami() {
        Subject subject = SecurityUtils.getSubject();
        if (subject == null) {
            throw new WebApplicationException("Can't determine current user: subject not found",
                    Status.INTERNAL_SERVER_ERROR);
        }

        UserPrincipal p = (UserPrincipal) subject.getPrincipal();
        if (p == null) {
            throw new WebApplicationException("Can't determine current user: entry not found",
                    Status.INTERNAL_SERVER_ERROR);
        }

        String displayName = null;

        LdapInfo ldapInfo = p.getLdapInfo();
        if (ldapInfo != null) {
            displayName = ldapInfo.getDisplayName();
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
    @RequiresAuthentication
    public boolean isProjectExists(@PathParam("orgName") @ConcordKey String orgName,
                                   @PathParam("projectName") String projectName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        return projectDao.getId(org.getId(), projectName) != null;
    }

    @GET
    @Path("/org/{orgName}/secret/{secretName}/exists")
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    public boolean isSecretExists(@PathParam("orgName") String orgName,
                                  @PathParam("secretName") String secretName) {
        try {
            OrganizationEntry org = orgManager.assertAccess(orgName, true);
            return secretManager.exists(org.getId(), secretName);
        } catch (UnauthorizedException e) {
            return false;
        }
    }

    @POST
    @Path("/repository/test")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    public boolean testRepository(RepositoryTestRequest req) {
        UUID orgId = req.getOrgId();

        try {
            repositoryManager.testConnection(orgId, req.getUrl(), req.getBranch(), req.getCommitId(), req.getPath(), req.getSecret());
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
