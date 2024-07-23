package com.walmartlabs.concord.console3.resources;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.console3.TemplateResponse;
import com.walmartlabs.concord.console3.model.OrganizationEntry;
import com.walmartlabs.concord.console3.model.UserContext;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.server.jooq.Tables.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.Tables.V_USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectDistinct;

@Path("/api/console3")
public class UserProfileResource implements Resource {

    private final DSLContext dsl;

    @Inject
    public UserProfileResource(@MainDB DSLContext dsl) {
        this.dsl = dsl;
    }

    @GET
    @Path("/profile/organizations")
    public TemplateResponse organizations(@Context UserContext user) {
        return new TemplateResponse("organizations.jte",
                Map.of("organizations", getUserOrganizations(user)));
    }

    private List<OrganizationEntry> getUserOrganizations(UserContext user) {
        var teamIds = select(V_USER_TEAMS.TEAM_ID)
                .from(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(user.userId()));

        var orgIds = selectDistinct(TEAMS.ORG_ID)
                .from(TEAMS)
                .where(TEAMS.TEAM_ID.in(teamIds));

        return dsl.selectFrom(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.in(orgIds))
                .fetch()
                .map(r -> new OrganizationEntry(r.get(ORGANIZATIONS.ORG_ID), r.get(ORGANIZATIONS.ORG_NAME)));
    }
}
