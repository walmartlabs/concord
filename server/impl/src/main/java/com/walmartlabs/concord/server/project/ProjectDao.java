package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.UpdateRepositoryRequest;
import com.walmartlabs.concord.server.jooq.tables.records.ProjectsRecord;
import com.walmartlabs.concord.server.jooq.tables.records.RepositoriesRecord;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.tables.ProjectKvStore.PROJECT_KV_STORE;
import static com.walmartlabs.concord.server.jooq.tables.ProjectTemplates.PROJECT_TEMPLATES;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Templates.TEMPLATES;

@Named
public class ProjectDao extends AbstractDao {

    private final UserPermissionCleaner permissionCleaner;

    @Inject
    public ProjectDao(Configuration cfg, UserPermissionCleaner permissionCleaner) {
        super(cfg);
        this.permissionCleaner = permissionCleaner;
    }

    public ProjectEntry get(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            ProjectsRecord r = tx.selectFrom(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(name))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            Set<String> templates = tx.select(PROJECT_TEMPLATES.TEMPLATE_NAME)
                    .from(PROJECT_TEMPLATES)
                    .where(PROJECT_TEMPLATES.PROJECT_NAME.eq(name))
                    .fetchSet(PROJECT_TEMPLATES.TEMPLATE_NAME);

            Result<RepositoriesRecord> repos = tx.selectFrom(REPOSITORIES)
                    .where(REPOSITORIES.PROJECT_NAME.eq(name))
                    .fetch();

            Map<String, UpdateRepositoryRequest> m = new HashMap<>();
            for (RepositoriesRecord repo : repos) {
                m.put(repo.getRepoName(), new UpdateRepositoryRequest(repo.getRepoUrl(),
                        repo.getRepoBranch(), repo.getRepoCommitId(), repo.getSecretName()));
            }

            return new ProjectEntry(r.getProjectName(), r.getDescription(), templates, m, null);
        }
    }

    public void insert(String name, String description, Collection<String> templateIds) {
        tx(tx -> insert(tx, name, description, templateIds));
    }

    public void insert(DSLContext tx, String name, String description, Collection<String> templateIds) {
        tx.insertInto(PROJECTS)
                .columns(PROJECTS.PROJECT_NAME, PROJECTS.DESCRIPTION)
                .values(name, description)
                .execute();

        insertTemplates(tx, name, templateIds);
    }

    public void update(DSLContext tx, String name, String description, Collection<String> templateIds) {
        tx.update(PROJECTS)
                .set(PROJECTS.DESCRIPTION, description)
                .where(PROJECTS.PROJECT_NAME.eq(name))
                .execute();

        tx.deleteFrom(PROJECT_TEMPLATES)
                .where(PROJECT_TEMPLATES.PROJECT_NAME.eq(name))
                .execute();

        insertTemplates(tx, name, templateIds);
    }

    public void delete(String id) {
        tx(tx -> delete(tx, id));
    }

    public void delete(DSLContext tx, String name) {
        permissionCleaner.onProjectRemoval(tx, name);

        tx.deleteFrom(PROJECT_KV_STORE)
                .where(PROJECT_KV_STORE.PROJECT_NAME.eq(name))
                .execute();

        tx.deleteFrom(PROJECTS)
                .where(PROJECTS.PROJECT_NAME.eq(name))
                .execute();
    }

    public List<ProjectEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectOnConditionStep<Record3<String, String, String>> query = selectCreateProjectRequest(tx);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return fold(query.fetch());
        }
    }

    public boolean exists(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(name)));
        }
    }

    private static void insertTemplates(DSLContext tx, String projectName, Collection<String> templates) {
        if (templates == null || templates.size() == 0) {
            return;
        }

        BatchBindStep b = tx.batch(tx.insertInto(PROJECT_TEMPLATES)
                .columns(PROJECT_TEMPLATES.PROJECT_NAME, PROJECT_TEMPLATES.TEMPLATE_NAME)
                .values((String) null, null));

        for (String tName : templates) {
            b.bind(projectName, tName);
        }

        b.execute();
    }

    private static List<ProjectEntry> fold(Result<Record3<String, String, String>> raw) {
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProjectEntry> result = new ArrayList<>();

        String lastName = null;
        String lastDescription = null;
        Set<String> lastTemplates = null;

        for (Record3<String, String, String> r : raw) {
            String rName = r.get(PROJECTS.PROJECT_NAME);
            String rDescription = r.get(PROJECTS.DESCRIPTION);
            String rTemplate = r.get(TEMPLATES.TEMPLATE_NAME);

            if (!rName.equals(lastName)) {
                if (lastName != null) {
                    add(result, lastName, lastDescription, lastTemplates);
                }

                lastName = rName;
                lastDescription = rDescription;
                lastTemplates = new HashSet<>();
            }

            if (rTemplate != null) {
                lastTemplates.add(rTemplate);
            }
        }

        add(result, lastName, lastDescription, lastTemplates);
        return result;
    }

    private static void add(List<ProjectEntry> l, String name, String description, Set<String> templates) {
        l.add(new ProjectEntry(name, description, templates, null, null));
    }

    private static SelectOnConditionStep<Record3<String, String, String>> selectCreateProjectRequest(DSLContext tx) {
        return tx.select(PROJECTS.PROJECT_NAME, PROJECTS.DESCRIPTION, TEMPLATES.TEMPLATE_NAME)
                .from(PROJECTS)
                .leftOuterJoin(PROJECT_TEMPLATES).on(PROJECT_TEMPLATES.PROJECT_NAME.eq(PROJECTS.PROJECT_NAME))
                .leftOuterJoin(TEMPLATES).on(PROJECT_TEMPLATES.TEMPLATE_NAME.eq(TEMPLATES.TEMPLATE_NAME));
    }
}
