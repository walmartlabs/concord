package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;


@Named
@Singleton
@Api(value = "UserLdapGroup", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/userldapgroup")
public class UserLdapGroup implements Resource {

    private final UserManager userManager;
    private final LdapUserInfoProvider ldapUserInfoProvider;
    private final LdapGroupDao ldapGroupsDao;

    @Inject
    public UserLdapGroup(UserManager userManager, LdapUserInfoProvider ldapUserInfoProvider, LdapGroupDao ldapGroupsDao) {
        this.userManager = userManager;
        this.ldapUserInfoProvider = ldapUserInfoProvider;
        this.ldapGroupsDao = ldapGroupsDao;
    }

    /**
     * Sync Ldap groups for a ldap user
     *
     * @param username username
     * @param domain domain
     * @return GenericOperationResult result
     */
    @POST
    @ApiOperation("Sync ldap groups for a user")
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user/{username}/domain/{domain}/sync")
    public GenericOperationResult sync(@ApiParam @PathParam("username") String username,
                                       @ApiParam @PathParam("domain") String domain) {
        assertAdmin();

        UUID id = userManager.getId(username, domain, UserType.LDAP).orElse(null);
        if (id == null) {
            throw new ConcordApplicationException("User not found: " + username, Response.Status.BAD_REQUEST);
        }

        UserInfoProvider.UserInfo info = ldapUserInfoProvider.getInfo(id, username, domain);
        if (info == null) {
            throw new ConcordApplicationException("User '" + username + "' with domain '" + domain + "' not found in LDAP", Response.Status.BAD_REQUEST);
        }
        
        Set<String> groups = info.groups();
        if (groups == null) {
            ldapGroupsDao.update(id, Collections.emptySet());
            ldapGroupsDao.updateLastSyncTimestamp(id);
        } else {
            ldapGroupsDao.update(id, groups);
        }
        
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }
}
