package com.walmartlabs.concord.server.org.jsonstore;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.ResourceAccessUtils;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserManager;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.UUID;

public class JsonStoreAccessManager {

    private final UserManager userManager;
    private final OrganizationManager orgManager;
    private final JsonStoreDao storeDao;

    @Inject
    public JsonStoreAccessManager(UserManager userManager, OrganizationManager orgManager, JsonStoreDao storeDao) {
        this.userManager = userManager;
        this.orgManager = orgManager;
        this.storeDao = storeDao;
    }

    public JsonStoreEntry assertAccess(UUID orgId, UUID storeId, String storeName, ResourceAccessLevel accessLevel, boolean orgMembersOnly) {
        JsonStoreEntry store;

        if (storeId != null) {
            store = storeDao.get(storeId);
        } else {
            store = storeDao.get(orgId, storeName);
        }

        if (store == null) {
            throw new ConcordApplicationException("JSON store not found: " + storeName, Response.Status.NOT_FOUND);
        }

        if (!hasAccess(store, accessLevel, orgMembersOnly)) {
            UserPrincipal p = UserPrincipal.getCurrent();
            throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't have " +
                    "the necessary access level (" + accessLevel + ") to the JSON store: " + store.name());
        }

        return store;
    }

    public boolean hasAccess(JsonStoreEntry store, ResourceAccessLevel accessLevel, boolean orgMembersOnly) {
        if (Roles.isAdmin()) {
            // an admin can access any store
            return true;
        }

        if (accessLevel == ResourceAccessLevel.READER && (Roles.isGlobalReader() || Roles.isGlobalWriter())) {
            return true;
        } else if (accessLevel == ResourceAccessLevel.WRITER && Roles.isGlobalWriter()) {
            return true;
        }

        UserPrincipal principal = UserPrincipal.assertCurrent();

        if (ResourceAccessUtils.isSame(principal, store.owner())) {
            // the owner can do anything with his store
            return true;
        }

        if (orgMembersOnly && store.visibility() == JsonStoreVisibility.PUBLIC
                && accessLevel == ResourceAccessLevel.READER
                && userManager.isInOrganization(store.orgId())) {
            // organization members can access any public store in the same organization
            return true;
        }

        OrganizationEntry org = orgManager.assertAccess(store.orgId(), false);
        if (ResourceAccessUtils.isSame(principal, org.getOwner())) {
            // the org owner can do anything with the org's store
            return true;
        }

        if (orgMembersOnly || store.visibility() != JsonStoreVisibility.PUBLIC) {
            if (!storeDao.hasAccessLevel(store.id(), principal.getId(), ResourceAccessLevel.atLeast(accessLevel))) {
                throw new UnauthorizedException("The current user (" + principal.getUsername() + ") doesn't have " +
                        "the necessary access level (" + accessLevel + ") to the JSON store: " + store.name());
            }
        }

        return true;
    }
}
