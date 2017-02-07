package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProjectTemplates.PROJECT_TEMPLATES;
import static com.walmartlabs.concord.server.jooq.public_.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.public_.tables.Templates.TEMPLATES;

@Named
public class ProjectDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(ProjectDao.class);

    @Inject
    public ProjectDao(Configuration cfg) {
        super(cfg);
    }

    public String getId(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(PROJECTS.PROJECT_ID)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(name))
                    .fetchOne(PROJECTS.PROJECT_ID);
        }
    }

    public String getName(String id) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(PROJECTS.PROJECT_NAME)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(id))
                    .fetchOne(PROJECTS.PROJECT_NAME);
        }
    }

    public void insert(String id, String name, String... templateIds) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            create.insertInto(PROJECTS)
                    .columns(PROJECTS.PROJECT_ID, PROJECTS.PROJECT_NAME)
                    .values(id, name)
                    .execute();

            insertTemplates(create, id, templateIds);
        });
        log.info("insert ['{}', '{}', {}] -> done", id, name, templateIds);
    }

    public void update(String id, String... templateIds) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            create.deleteFrom(PROJECT_TEMPLATES)
                    .where(PROJECT_TEMPLATES.PROJECT_ID.eq(id))
                    .execute();

            insertTemplates(create, id, templateIds);
        });
        log.info("update ['{}', {}] -> done", id, templateIds);
    }

    public void delete(String id) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            create.deleteFrom(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(id))
                    .execute();
        });
        log.info("delete ['{}'] -> done", id);
    }

    public List<ProjectEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectJoinStep<Record3<String, String, String>> query = create
                    .select(PROJECTS.PROJECT_ID, PROJECTS.PROJECT_NAME, TEMPLATES.TEMPLATE_NAME)
                    .from(PROJECTS)
                    .leftOuterJoin(PROJECT_TEMPLATES).on(PROJECT_TEMPLATES.PROJECT_ID.eq(PROJECTS.PROJECT_ID))
                    .leftOuterJoin(TEMPLATES).on(PROJECT_TEMPLATES.TEMPLATE_ID.eq(TEMPLATES.TEMPLATE_ID));

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            List<ProjectEntry> result = fold(query.fetch());
            log.info("list [{}, {}] -> got {} result(s)", sortField, asc, result.size());
            return result;
        }
    }

    public boolean exists(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            int cnt = create.fetchCount(create.selectFrom(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(name)));

            return cnt > 0;
        }
    }

    private static void insertTemplates(DSLContext create, String projectId, String... templateIds) {
        if (templateIds == null || templateIds.length == 0) {
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
        List<String> lastTemplates = null;

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
                lastTemplates = new ArrayList<>();
            }

            if (rTemplate != null) {
                lastTemplates.add(rTemplate);
            }
        }

        add(result, lastId, lastName, lastTemplates);
        return result;
    }

    private static void add(List<ProjectEntry> l, String id, String name, List<String> templates) {
        Collections.sort(templates);
        l.add(new ProjectEntry(id, name, templates.toArray(new String[templates.size()])));
    }
}
