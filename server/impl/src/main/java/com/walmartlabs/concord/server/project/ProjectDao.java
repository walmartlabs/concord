package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProjectTemplates.PROJECT_TEMPLATES;
import static com.walmartlabs.concord.server.jooq.public_.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.public_.tables.Templates.TEMPLATES;

@Named
public class ProjectDao extends AbstractDao {

    private final UserPermissionCleaner permissionCleaner;

    @Inject
    public ProjectDao(Configuration cfg, UserPermissionCleaner permissionCleaner) {
        super(cfg);
        this.permissionCleaner = permissionCleaner;
    }

    public String getId(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(PROJECTS.PROJECT_ID)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(name))
                    .fetchOne(PROJECTS.PROJECT_ID);
        }
    }

    public ProjectEntry getByName(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return foldOne(selectProjectEntry(create)
                    .where(PROJECTS.PROJECT_NAME.eq(name)).fetch());
        }
    }

    public void insert(String id, String name, Collection<String> templateIds) {
        tx(tx -> {
            insert(tx, id, name, templateIds);
        });
    }

    public void insert(DSLContext create, String id, String name, Collection<String> templateIds) {
        create.insertInto(PROJECTS)
                .columns(PROJECTS.PROJECT_ID, PROJECTS.PROJECT_NAME)
                .values(id, name)
                .execute();

        insertTemplates(create, id, templateIds);
    }

    public void update(DSLContext create, String id, Collection<String> templateIds) {
        create.deleteFrom(PROJECT_TEMPLATES)
                .where(PROJECT_TEMPLATES.PROJECT_ID.eq(id))
                .execute();

        insertTemplates(create, id, templateIds);
    }

    public void delete(String id) {
        tx(tx -> {
            delete(tx, id);
        });
    }

    public void delete(DSLContext create, String id) {
        String name = getName(create, id);
        permissionCleaner.onProjectRemoval(create, id, name);

        create.deleteFrom(PROJECTS)
                .where(PROJECTS.PROJECT_ID.eq(id))
                .execute();
    }

    public List<ProjectEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectOnConditionStep<Record3<String, String, String>> query = selectProjectEntry(create);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return fold(query.fetch());
        }
    }

    public boolean exists(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            int cnt = create.fetchCount(create.selectFrom(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(name)));

            return cnt > 0;
        }
    }

    private static String getName(DSLContext create, String id) {
        return create.select(PROJECTS.PROJECT_NAME)
                .from(PROJECTS)
                .where(PROJECTS.PROJECT_ID.eq(id))
                .fetchOne(PROJECTS.PROJECT_NAME);
    }

    private static void insertTemplates(DSLContext create, String projectId, Collection<String> templateIds) {
        if (templateIds == null || templateIds.size() == 0) {
            return;
        }

        BatchBindStep b = create.batch(create.insertInto(PROJECT_TEMPLATES)
                .columns(PROJECT_TEMPLATES.PROJECT_ID, PROJECT_TEMPLATES.TEMPLATE_ID)
                .values((String) null, null));

        for (String tId : templateIds) {
            b.bind(projectId, tId);
        }

        b.execute();
    }

    private static List<ProjectEntry> fold(Result<Record3<String, String, String>> raw) {
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProjectEntry> result = new ArrayList<>();

        String lastId = null;
        String lastName = null;
        Set<String> lastTemplates = null;

        for (Record3<String, String, String> r : raw) {
            String rId = r.get(PROJECTS.PROJECT_ID);
            String rName = r.get(PROJECTS.PROJECT_NAME);
            String rTemplate = r.get(TEMPLATES.TEMPLATE_NAME);

            if (lastId != rId) {
                if (lastId != null) {
                    add(result, lastId, lastName, lastTemplates);
                }

                lastId = rId;
                lastName = rName;
                lastTemplates = new HashSet<>();
            }

            if (rTemplate != null) {
                lastTemplates.add(rTemplate);
            }
        }

        add(result, lastId, lastName, lastTemplates);
        return result;
    }

    private static ProjectEntry foldOne(Result<Record3<String, String, String>> raw) {
        if (raw.isEmpty()) {
            return null;
        }

        String id = null;
        String name = null;
        Set<String> templates = null;

        for (Record3<String, String, String> r : raw) {
            id = r.get(PROJECTS.PROJECT_ID);
            name = r.get(PROJECTS.PROJECT_NAME);
            String template = r.get(TEMPLATES.TEMPLATE_NAME);

            if (template != null) {
                if (templates == null) {
                    templates = new HashSet<>();
                }
                templates.add(template);
            }
        }

        return new ProjectEntry(id, name, templates);
    }

    private static void add(List<ProjectEntry> l, String id, String name, Set<String> templates) {
        l.add(new ProjectEntry(id, name, templates));
    }

    private static SelectOnConditionStep<Record3<String, String, String>> selectProjectEntry(DSLContext create) {
        return create.select(PROJECTS.PROJECT_ID, PROJECTS.PROJECT_NAME, TEMPLATES.TEMPLATE_NAME)
                .from(PROJECTS)
                .leftOuterJoin(PROJECT_TEMPLATES).on(PROJECT_TEMPLATES.PROJECT_ID.eq(PROJECTS.PROJECT_ID))
                .leftOuterJoin(TEMPLATES).on(PROJECT_TEMPLATES.TEMPLATE_ID.eq(TEMPLATES.TEMPLATE_ID));
    }
}
