package com.walmartlabs.concord.server.landing;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.landing.LandingEntry;
import com.walmartlabs.concord.server.api.project.ProjectVisibility;
import com.walmartlabs.concord.server.jooq.tables.LandingPage;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.postgresql.util.Base64;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.LandingPage.LANDING_PAGE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
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

        Field<String> projectNameField = select(PROJECTS.PROJECT_NAME).from(PROJECTS).where(PROJECTS.PROJECT_ID.eq(lp.PROJECT_ID)).asField();
        Field<String> repositoryNameField = select(REPOSITORIES.REPO_NAME).from(REPOSITORIES).where(REPOSITORIES.REPO_ID.eq(lp.REPO_ID)).asField();

        try (DSLContext tx = DSL.using(cfg)) {
            return tx
                    .select(lp.LANDING_PAGE_ID, projectNameField, repositoryNameField, lp.NAME, lp.DESCRIPTION, lp.ICON)
                    .from(lp)
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

    public List<LandingEntry> list(UUID currentUserId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return list(tx, currentUserId);
        }
    }

    private List<LandingEntry> list(DSLContext tx, UUID currentUserId) {
        LandingPage lp = LANDING_PAGE.as("lp");
        Projects p = PROJECTS.as("p");

        Field<String> repositoryNameField = select(REPOSITORIES.REPO_NAME).from(REPOSITORIES).where(REPOSITORIES.REPO_ID.eq(lp.REPO_ID)).asField();

        Condition filterByTeamMember = exists(selectFrom(USER_TEAMS)
                .where(USER_TEAMS.USER_ID.eq(currentUserId)
                        .and(USER_TEAMS.TEAM_ID.eq(p.TEAM_ID))));

        SelectJoinStep<Record6<UUID, String, String, String, String, byte[]>> q =
                tx.select(lp.LANDING_PAGE_ID, p.PROJECT_NAME, repositoryNameField, lp.NAME, lp.DESCRIPTION, lp.ICON)
                .from(lp)
                .innerJoin(p).on(lp.PROJECT_ID.eq(p.PROJECT_ID));

        if (currentUserId != null) {
            q.where(or(p.VISIBILITY.eq(ProjectVisibility.PUBLIC.toString()), filterByTeamMember));
        }

        return q.orderBy(lp.NAME)
            .fetch(LandingDao::toEntity);
    }

    private static LandingEntry toEntity(Record6<UUID, String, String, String, String, byte[]> item) {
        String icon = null;
        if (item.value6() != null) {
           icon = Base64.encodeBytes(item.value6());
        }
        return new LandingEntry(item.value1(), item.value2(), item.value3(), item.value4(), item.value5(), icon);
    }
}
