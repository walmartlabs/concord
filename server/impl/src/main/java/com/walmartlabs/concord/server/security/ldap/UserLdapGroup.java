package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;


@Named
@Singleton
@Path("/api/v1/userldapgroup")
@Tag(name = "UserLdapGroup")
public class UserLdapGroup implements Resource {

    private final UserManager userManager;
    private final LdapUserInfoProvider ldapUserInfoProvider;
    private final LdapGroupDao ldapGroupsDao;
    private final LdapManager ldapManager;

    @Inject
    public UserLdapGroup(UserManager userManager, LdapUserInfoProvider ldapUserInfoProvider, LdapGroupDao ldapGroupsDao, LdapManager ldapManager) {
        this.userManager = userManager;
        this.ldapUserInfoProvider = ldapUserInfoProvider;
        this.ldapGroupsDao = ldapGroupsDao;
        this.ldapManager = ldapManager;
    }

    /**
     * Sync Ldap groups for a ldap user
     *
     * @param req user's data
     * @return GenericOperationResult result
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/sync")
    @Validate
    @Operation(description = "Sync ldap groups for a user", operationId = "syncLdapGroups")
    public GenericOperationResult sync(@Valid SyncUserLdapGroupRequest req) {
        assertAdmin();

        UUID id = userManager.getId(req.getUsername(), req.getUserDomain(), UserType.LDAP).orElse(null);
        if (id == null) {
            throw new ConcordApplicationException("User not found: " + req.getUsername(), Response.Status.BAD_REQUEST);
        }

        UserInfoProvider.UserInfo info = ldapUserInfoProvider.getInfo(id, req.getUsername(), req.getUserDomain());
        if (info == null) {
            throw new ConcordApplicationException("User '" + req.getUsername() + "' with domain '" + req.getUserDomain() + "' not found in LDAP", Response.Status.BAD_REQUEST);
        }

        try {
            Set<String> groups = ldapManager.getGroups(req.getUsername(), req.getUserDomain());
            if (groups == null) {
                ldapGroupsDao.update(id, Collections.emptySet());
            } else {
                ldapGroupsDao.update(id, groups);
            }
        } catch (Exception e) {
            throw new ConcordApplicationException("Failed to update groups for user '" + req.getUsername() + "' error -> '" + e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new UnauthorizedException("Only admins can do that");
        }
    }
}
