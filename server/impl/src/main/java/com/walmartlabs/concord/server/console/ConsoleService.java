package com.walmartlabs.concord.server.console;

import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapInfo;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.subject.Subject;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Named
@Path("/api/service/console")
public class ConsoleService implements Resource {

    private final ProcessResource processResource;

    @Inject
    public ConsoleService(ProcessResource processResource) {
        this.processResource = processResource;
    }

    @GET
    @Path("/whoami")
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    public UserResponse whoami() {
        Subject subject = SecurityUtils.getSubject();
        if (subject == null) {
            throw new WebApplicationException("Can't determine current user: subject not found",
                    Response.Status.INTERNAL_SERVER_ERROR);
        }

        UserPrincipal u = (UserPrincipal) subject.getPrincipal();
        if (u == null) {
            throw new WebApplicationException("Can't determine current user: entry not found",
                    Response.Status.INTERNAL_SERVER_ERROR);
        }

        LdapInfo i = u.getLdapInfo();

        String displayName = i.getDisplayName();
        if (displayName == null) {
            displayName = u.getUsername();
        }

        return new UserResponse(u.getRealm(), u.getUsername(), displayName);
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
}
