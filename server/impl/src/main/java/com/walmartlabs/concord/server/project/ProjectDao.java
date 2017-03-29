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

    public ProjectEntry get(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return foldOne(selectProjectEntry(create)
                    .where(PROJECTS.PROJECT_NAME.eq(name)).fetch());
        }
    }

    public void insert(String name, Collection<String> templateIds) {
        tx(tx -> {
            insert(tx, name, templateIds);
        });
    }

    public void insert(DSLContext create, String name, Collection<String> templateIds) {
        create.insertInto(PROJECTS)
                .columns(PROJECTS.PROJECT_NAME)
                .values(name)
                .execute();

        insertTemplates(create, name, templateIds);
    }

    public void update(DSLContext create, String name, Collection<String> templateIds) {
        create.deleteFrom(PROJECT_TEMPLATES)
                .where(PROJECT_TEMPLATES.PROJECT_NAME.eq(name))
                .execute();

        insertTemplates(create, name, templateIds);
    }

    public void delete(String id) {
        tx(tx -> {
            delete(tx, id);
        });
    }

    public void delete(DSLContext create, String name) {
        permissionCleaner.onProjectRemoval(create, name);
        create.deleteFrom(PROJECTS)
                .where(PROJECTS.PROJECT_NAME.eq(name))
                .execute();
    }

    public List<ProjectEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectOnConditionStep<Record2<String, String>> query = selectProjectEntry(create);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return fold(query.fetch());
        }
    }

    public boolean exists(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.fetchExists(create.selectFrom(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(name)));
        }
    }

    private static void insertTemplates(DSLContext create, String projectName, Collection<String> templates) {
        if (templates == null || templates.size() == 0) {
            return;
        }

        BatchBindStep b = create.batch(create.insertInto(PROJECT_TEMPLATES)
                .columns(PROJECT_TEMPLATES.PROJECT_NAME, PROJECT_TEMPLATES.TEMPLATE_NAME)
                .values((String) null, null));

        for (String tName : templates) {
            b.bind(projectName, tName);
        }

        b.execute();
    }

    private static List<ProjectEntry> fold(Result<Record2<String, String>> raw) {
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProjectEntry> result = new ArrayList<>();

        String lastName = null;
        Set<String> lastTemplates = null;

        for (Record2<String, String> r : raw) {
            String rName = r.get(PROJECTS.PROJECT_NAME);
            String rTemplate = r.get(TEMPLATES.TEMPLATE_NAME);

            if (!rName.equals(lastName)) {
                if (lastName != null) {
                    add(result, lastName, lastTemplates);
                }

                lastName = rName;
                lastTemplates = new HashSet<>();
            }

            if (rTemplate != null) {
                lastTemplates.add(rTemplate);
            }
        }

        add(result, lastName, lastTemplates);
        return result;
    }

    private static ProjectEntry foldOne(Result<Record2<String, String>> raw) {
        if (raw.isEmpty()) {
            return null;
        }

        String name = null;
        Set<String> templates = null;

        for (Record2<String, String> r : raw) {
            name = r.get(PROJECTS.PROJECT_NAME);
            String template = r.get(TEMPLATES.TEMPLATE_NAME);

            if (template != null) {
                if (templates == null) {
                    templates = new HashSet<>();
                }
                templates.add(template);
            }
        }

        return new ProjectEntry(name, templates);
    }

    private static void add(List<ProjectEntry> l, String name, Set<String> templates) {
        l.add(new ProjectEntry(name, templates));
    }

    private static SelectOnConditionStep<Record2<String, String>> selectProjectEntry(DSLContext create) {
        return create.select(PROJECTS.PROJECT_NAME, TEMPLATES.TEMPLATE_NAME)
                .from(PROJECTS)
                .leftOuterJoin(PROJECT_TEMPLATES).on(PROJECT_TEMPLATES.PROJECT_NAME.eq(PROJECTS.PROJECT_NAME))
                .leftOuterJoin(TEMPLATES).on(PROJECT_TEMPLATES.TEMPLATE_NAME.eq(TEMPLATES.TEMPLATE_NAME));
    }
}
