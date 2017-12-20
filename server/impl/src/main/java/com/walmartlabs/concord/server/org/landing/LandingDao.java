package com.walmartlabs.concord.server.org.landing;

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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.org.landing.LandingEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectVisibility;
import com.walmartlabs.concord.server.jooq.tables.LandingPage;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.jooq.tables.Repositories;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.postgresql.util.Base64;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.LandingPage.LANDING_PAGE;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static org.jooq.impl.DSL.*;

@Named
public class LandingDao extends AbstractDao {

    @Inject
    public LandingDao(Configuration cfg) {
        super(cfg);
    }

    public LandingEntry get(UUID id) {
        LandingPage lp = LANDING_PAGE.as("lp");
        Projects p = PROJECTS.as("p");
        Repositories r = REPOSITORIES.as("r");

        Field<String> orgNameField = select(ORGANIZATIONS.ORG_NAME).from(ORGANIZATIONS).where(ORGANIZATIONS.ORG_ID.eq(p.ORG_ID)).asField();

        try (DSLContext tx = DSL.using(cfg)) {
            return tx
                    .select(lp.LANDING_PAGE_ID,
                            p.ORG_ID,
                            orgNameField,
                            lp.PROJECT_ID,
                            p.PROJECT_NAME,
                            r.REPO_NAME,
                            lp.NAME,
                            lp.DESCRIPTION,
                            lp.ICON)
                    .from(lp)
                    .innerJoin(p).on(p.PROJECT_ID.eq(lp.PROJECT_ID))
                    .innerJoin(r).on(r.REPO_ID.eq(lp.REPO_ID))
                    .where(lp.LANDING_PAGE_ID.eq(id))
                    .fetchOne(LandingDao::toEntity);
        }
    }

    public UUID insert(UUID projectId, UUID repositoryId, String name, String description, byte[] icon) {
        return txResult(tx -> insert(tx, projectId, repositoryId, name, description, icon));
    }

    public UUID insert(DSLContext tx, UUID projectId, UUID repositoryId, String name, String description, byte[] icon) {
        return tx.insertInto(LANDING_PAGE)
                .columns(LANDING_PAGE.PROJECT_ID, LANDING_PAGE.REPO_ID, LANDING_PAGE.NAME, LANDING_PAGE.DESCRIPTION, LANDING_PAGE.ICON)
                .values(projectId, repositoryId, name, description, icon)
                .returning(LANDING_PAGE.LANDING_PAGE_ID)
                .fetchOne()
                .getLandingPageId();
    }

    public void update(UUID id, UUID projectId, UUID repositoryId, String name, String description, byte[] icon) {
        tx(tx -> update(tx, id, projectId, repositoryId, name, description, icon));
    }

    private void update(DSLContext tx, UUID id, UUID projectId, UUID repositoryId, String name, String description, byte[] icon) {
        tx.update(LANDING_PAGE)
                .set(LANDING_PAGE.PROJECT_ID, projectId)
                .set(LANDING_PAGE.REPO_ID, repositoryId)
                .set(LANDING_PAGE.NAME, name)
                .set(LANDING_PAGE.DESCRIPTION, description)
                .set(LANDING_PAGE.ICON, icon)
                .where(LANDING_PAGE.LANDING_PAGE_ID.eq(id))
                .execute();
    }

    public void delete(UUID id) {
        tx(tx -> tx.delete(LANDING_PAGE)
                .where(LANDING_PAGE.LANDING_PAGE_ID.eq(id))
                .execute());
    }

    public void delete(DSLContext tx, UUID projectId, UUID repositoryId) {
        tx.delete(LANDING_PAGE)
                .where(LANDING_PAGE.PROJECT_ID.eq(projectId)
                        .and(LANDING_PAGE.REPO_ID.eq(repositoryId)))
                .execute();
    }

    public List<LandingEntry> list(UUID orgId, UUID currentUserId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return list(tx, orgId, currentUserId);
        }
    }

    private List<LandingEntry> list(DSLContext tx, UUID orgId, UUID currentUserId) {
        LandingPage lp = LANDING_PAGE.as("lp");
        Projects p = PROJECTS.as("p");
        Repositories r = REPOSITORIES.as("r");

        Field<String> orgNameField = select(ORGANIZATIONS.ORG_NAME).from(ORGANIZATIONS).where(ORGANIZATIONS.ORG_ID.eq(p.ORG_ID)).asField();

        SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID)
                .from(TEAMS)
                .where(TEAMS.ORG_ID.eq(orgId));

        Condition filterByTeamMember = exists(selectOne().from(USER_TEAMS)
                .where(USER_TEAMS.USER_ID.eq(currentUserId)
                        .and(USER_TEAMS.TEAM_ID.in(teamIds))));

        SelectJoinStep<Record9<UUID, UUID, String, UUID, String, String, String, String, byte[]>> q =
                tx.select(lp.LANDING_PAGE_ID,
                        p.ORG_ID,
                        orgNameField,
                        lp.PROJECT_ID,
                        p.PROJECT_NAME,
                        r.REPO_NAME,
                        lp.NAME,
                        lp.DESCRIPTION,
                        lp.ICON)
                        .from(lp)
                        .innerJoin(p).on(p.PROJECT_ID.eq(lp.PROJECT_ID))
                        .innerJoin(r).on(r.REPO_ID.eq(lp.REPO_ID));

        if (currentUserId != null) {
            q.where(or(p.VISIBILITY.eq(ProjectVisibility.PUBLIC.toString()), filterByTeamMember));
        }

        return q.orderBy(lp.NAME)
                .fetch(LandingDao::toEntity);
    }

    private static LandingEntry toEntity(Record9<UUID, UUID, String, UUID, String, String, String, String, byte[]> item) {
        String icon = null;
        if (item.value6() != null) {
            icon = Base64.encodeBytes(item.value9());
        }
        return new LandingEntry(item.value1(), item.value2(), item.value3(),
                item.value4(), item.value5(), item.value6(),
                item.value7(), item.value8(), icon);
    }
}
