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

import static com.walmartlabs.concord.server.jooq.tables.ProjectTemplates.PROJECT_TEMPLATES;
import static com.walmartlabs.concord.server.jooq.tables.Templates.TEMPLATES;

@Named
public class TemplateDao extends AbstractDao {

    private final UserPermissionCleaner permissionCleaner;

    @Inject
    public TemplateDao(Configuration cfg, UserPermissionCleaner permissionCleaner) {
        super(cfg);
        this.permissionCleaner = permissionCleaner;
    }

    public Collection<String> getProjectTemplates(String projectName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECT_TEMPLATES.TEMPLATE_NAME)
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
        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record2<String, Integer>> query = tx
                    .select(TEMPLATES.TEMPLATE_NAME, TEMPLATES.TEMPLATE_DATA.length().as("SIZE"))
                    .from(TEMPLATES);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(r -> new TemplateEntry(r.get(TEMPLATES.TEMPLATE_NAME), (Integer) r.get("SIZE")));
        }
    }

    public void insert(String name, InputStream data) {
        Function<DSLContext, String> sqlFn = tx -> tx.insertInto(TEMPLATES)
                .columns(TEMPLATES.TEMPLATE_NAME, TEMPLATES.TEMPLATE_DATA)
                .values((String) null, null)
                .getSQL();

        tx(tx -> executeUpdate(tx, sqlFn, ps -> {
            ps.setString(1, name);
            ps.setBinaryStream(2, data);
        }));
    }

    public void update(String name, InputStream data) {
        Function<DSLContext, String> sqlFn = create -> create.update(TEMPLATES)
                .set(TEMPLATES.TEMPLATE_DATA, (byte[]) null)
                .where(TEMPLATES.TEMPLATE_NAME.eq(name))
                .getSQL();

        tx(tx -> executeUpdate(tx, sqlFn, ps -> {
            ps.setBinaryStream(1, data);
            ps.setString(2, name);
        }));
    }

    public void delete(String name) {
        tx(tx -> {
            permissionCleaner.onTemplateRemoval(tx, name);

            tx.deleteFrom(PROJECT_TEMPLATES)
                    .where(PROJECT_TEMPLATES.TEMPLATE_NAME.eq(name))
                    .execute();

            tx.deleteFrom(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_NAME.eq(name))
                    .execute();
        });
    }

    public boolean exists(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            int cnt = tx.fetchCount(tx.selectFrom(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_NAME.eq(name)));

            return cnt > 0;
        }
    }
}
