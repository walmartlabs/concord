package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.bootstrap.db.AbstractDao;
import com.walmartlabs.concord.server.repository.RepositoryDao;
import org.jooq.BatchBindStep;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProjectTemplates.PROJECT_TEMPLATES;
import static com.walmartlabs.concord.server.jooq.public_.tables.Projects.PROJECTS;

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
            deleteTemplates(create, id);
            insertTemplates(create, id, templateIds);
        });
    }

    public void delete(String id) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            create.deleteFrom(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(id))
                    .execute();
        });
    }

    private void insertTemplates(DSLContext create, String projectId, String... templateIds) {
        if (templateIds != null) {
            BatchBindStep b = create.batch(create.insertInto(PROJECT_TEMPLATES)
                    .columns(PROJECT_TEMPLATES.PROJECT_ID, PROJECT_TEMPLATES.TEMPLATE_ID)
                    .values((String) null, null));

            for (String tId : templateIds) {
                b.bind(projectId, tId);
            }

            b.execute();
        }
    }

    private void deleteTemplates(DSLContext create, String projectId) {
        create.deleteFrom(PROJECT_TEMPLATES)
                .where(PROJECT_TEMPLATES.PROJECT_ID.eq(projectId))
                .execute();
    }
}
