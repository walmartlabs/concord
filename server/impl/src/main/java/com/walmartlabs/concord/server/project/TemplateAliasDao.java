package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.TemplateAliasEntry;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Optional;

import static com.walmartlabs.concord.server.jooq.tables.TemplateAliases.TEMPLATE_ALIASES;

@Named
public class TemplateAliasDao extends AbstractDao {

    @Inject
    public TemplateAliasDao(Configuration cfg) {
        super(cfg);
    }

    public boolean exists(String alias) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(TEMPLATE_ALIASES)
                    .where(TEMPLATE_ALIASES.TEMPLATE_ALIAS.eq(alias)));
        }
    }

    public Optional<String> get(String alias) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(TEMPLATE_ALIASES.TEMPLATE_URL)
                    .from(TEMPLATE_ALIASES)
                    .where(TEMPLATE_ALIASES.TEMPLATE_ALIAS.eq(alias))
                    .fetchOptional(TEMPLATE_ALIASES.TEMPLATE_URL);
        }
    }

    public void insert(String alias, String url) {
        tx(tx -> insert(tx, alias, url));
    }

    public void insert(DSLContext tx, String alias, String url) {
        tx.insertInto(TEMPLATE_ALIASES)
                .columns(TEMPLATE_ALIASES.TEMPLATE_ALIAS, TEMPLATE_ALIASES.TEMPLATE_URL)
                .values(alias, url)
                .execute();
    }

    public List<TemplateAliasEntry> list() {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(TEMPLATE_ALIASES.TEMPLATE_ALIAS, TEMPLATE_ALIASES.TEMPLATE_URL)
                    .from(TEMPLATE_ALIASES)
                    .fetch(TemplateAliasDao::toEntry);
        }
    }

    public void delete(String alias) {
        tx(tx -> delete(tx, alias));
    }

    public void delete(DSLContext tx, String alias) {
        tx.deleteFrom(TEMPLATE_ALIASES)
                .where(TEMPLATE_ALIASES.TEMPLATE_ALIAS.eq(alias))
                .execute();
    }

    private static TemplateAliasEntry toEntry(Record2<String, String> r) {
        return new TemplateAliasEntry(r.value1(), r.value2());
    }
}
