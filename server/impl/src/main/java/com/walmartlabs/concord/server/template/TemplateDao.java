package com.walmartlabs.concord.server.template;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.template.TemplateEntry;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProjectTemplates.PROJECT_TEMPLATES;
import static com.walmartlabs.concord.server.jooq.public_.tables.Templates.TEMPLATES;

@Named
public class TemplateDao extends AbstractDao {

    private final UserPermissionCleaner permissionCleaner;

    @Inject
    public TemplateDao(Configuration cfg, UserPermissionCleaner permissionCleaner) {
        super(cfg);
        this.permissionCleaner = permissionCleaner;
    }

    public Collection<String> getProjectTemplateIds(String projectId) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(PROJECT_TEMPLATES.TEMPLATE_ID)
                    .from(PROJECT_TEMPLATES)
                    .where(PROJECT_TEMPLATES.PROJECT_ID.eq(projectId))
                    .fetch(PROJECT_TEMPLATES.TEMPLATE_ID);
        }
    }

    public InputStream get(String id) {
        Function<DSLContext, String> sql = create -> create.select(TEMPLATES.TEMPLATE_DATA)
                .from(TEMPLATES)
                .where(TEMPLATES.TEMPLATE_ID.eq(id))
                .getSQL();

        return getData(sql, ps -> ps.setString(1, id), 1);
    }

    public List<TemplateEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectJoinStep<Record2<String, String>> query = create
                    .select(TEMPLATES.TEMPLATE_ID, TEMPLATES.TEMPLATE_NAME)
                    .from(TEMPLATES);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(r -> new TemplateEntry(r.get(TEMPLATES.TEMPLATE_ID), r.get(TEMPLATES.TEMPLATE_NAME)));
        }
    }

    public void insert(String id, String name, InputStream data) {
        Function<DSLContext, String> sqlFn = create -> create.insertInto(TEMPLATES)
                .columns(TEMPLATES.TEMPLATE_ID, TEMPLATES.TEMPLATE_NAME, TEMPLATES.TEMPLATE_DATA)
                .values((String) null, null, null)
                .getSQL();

        tx(tx -> {
            executeUpdate(tx, sqlFn, ps -> {
                ps.setString(1, id);
                ps.setString(2, name);
                ps.setBinaryStream(3, data);
            });
        });
    }

    public void update(String id, InputStream data) {
        Function<DSLContext, String> sqlFn = create -> create.update(TEMPLATES)
                .set(TEMPLATES.TEMPLATE_DATA, (byte[]) null)
                .where(TEMPLATES.TEMPLATE_ID.eq(id))
                .getSQL();

        tx(tx -> {
            executeUpdate(tx, sqlFn, ps -> {
                ps.setBinaryStream(1, data);
                ps.setString(2, id);
            });
        });
    }

    public void delete(String id) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            String name = getName(create, id);
            permissionCleaner.onTemplateRemoval(create, name);

            create.deleteFrom(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_ID.eq(id))
                    .execute();
        });
    }

    public String getId(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(TEMPLATES.TEMPLATE_ID)
                    .from(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_NAME.eq(name))
                    .fetchOne(TEMPLATES.TEMPLATE_ID);
        }
    }

    public boolean exists(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            int cnt = create.fetchCount(create.selectFrom(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_NAME.eq(name)));

            return cnt > 0;
        }
    }

    public boolean existsId(String id) {
        try (DSLContext create = DSL.using(cfg)) {
            int cnt = create.fetchCount(create.selectFrom(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_ID.eq(id)));

            return cnt > 0;
        }
    }

    private static String getName(DSLContext create, String id) {
        return create.select(TEMPLATES.TEMPLATE_NAME)
                .from(TEMPLATES)
                .where(TEMPLATES.TEMPLATE_ID.eq(id))
                .fetchOne(TEMPLATES.TEMPLATE_NAME);
    }
}
