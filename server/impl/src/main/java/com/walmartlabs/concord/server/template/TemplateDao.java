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

    public Collection<String> getProjectTemplates(String projectName) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(PROJECT_TEMPLATES.TEMPLATE_NAME)
                    .from(PROJECT_TEMPLATES)
                    .where(PROJECT_TEMPLATES.PROJECT_NAME.eq(projectName))
                    .fetch(PROJECT_TEMPLATES.TEMPLATE_NAME);
        }
    }

    public InputStream getData(String name) {
        Function<DSLContext, String> sql = create -> create.select(TEMPLATES.TEMPLATE_DATA)
                .from(TEMPLATES)
                .where(TEMPLATES.TEMPLATE_NAME.eq(name))
                .getSQL();

        return getData(sql, ps -> ps.setString(1, name), 1);
    }

    public List<TemplateEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectJoinStep<Record2<String, Integer>> query = create
                    .select(TEMPLATES.TEMPLATE_NAME, TEMPLATES.TEMPLATE_DATA.length().as("SIZE"))
                    .from(TEMPLATES);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(r -> new TemplateEntry(r.get(TEMPLATES.TEMPLATE_NAME), (Integer) r.get("SIZE")));
        }
    }

    public void insert(String name, InputStream data) {
        Function<DSLContext, String> sqlFn = create -> create.insertInto(TEMPLATES)
                .columns(TEMPLATES.TEMPLATE_NAME, TEMPLATES.TEMPLATE_DATA)
                .values((String) null, null)
                .getSQL();

        tx(tx -> {
            executeUpdate(tx, sqlFn, ps -> {
                ps.setString(1, name);
                ps.setBinaryStream(2, data);
            });
        });
    }

    public void update(String name, InputStream data) {
        Function<DSLContext, String> sqlFn = create -> create.update(TEMPLATES)
                .set(TEMPLATES.TEMPLATE_DATA, (byte[]) null)
                .where(TEMPLATES.TEMPLATE_NAME.eq(name))
                .getSQL();

        tx(tx -> {
            executeUpdate(tx, sqlFn, ps -> {
                ps.setBinaryStream(1, data);
                ps.setString(2, name);
            });
        });
    }

    public void delete(String name) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            permissionCleaner.onTemplateRemoval(create, name);
            create.deleteFrom(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_NAME.eq(name))
                    .execute();
        });
    }

    public boolean exists(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            int cnt = create.fetchCount(create.selectFrom(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_NAME.eq(name)));

            return cnt > 0;
        }
    }
}
