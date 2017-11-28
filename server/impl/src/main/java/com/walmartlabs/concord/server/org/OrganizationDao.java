package com.walmartlabs.concord.server.org;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;

@Named
public class OrganizationDao extends AbstractDao {

    @Inject
    public OrganizationDao(Configuration cfg) {
        super(cfg);
    }

    @Override
    public <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public OrganizationEntry get(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(ORGANIZATIONS.ORG_ID, ORGANIZATIONS.ORG_NAME)
                    .from(ORGANIZATIONS)
                    .where(ORGANIZATIONS.ORG_ID.eq(id))
                    .fetchOne(OrganizationDao::toEntry);
        }
    }

    public UUID getId(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(ORGANIZATIONS.ORG_ID)
                    .from(ORGANIZATIONS)
                    .where(ORGANIZATIONS.ORG_NAME.eq(name))
                    .fetchOne(ORGANIZATIONS.ORG_ID);
        }
    }

    public OrganizationEntry getByName(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(ORGANIZATIONS.ORG_ID, ORGANIZATIONS.ORG_NAME)
                    .from(ORGANIZATIONS)
                    .where(ORGANIZATIONS.ORG_NAME.eq(name))
                    .fetchOne(OrganizationDao::toEntry);
        }
    }

    public UUID insert(String name) {
        return txResult(tx -> insert(tx, name));
    }

    public UUID insert(DSLContext tx, String name) {
        return tx.insertInto(ORGANIZATIONS)
                .columns(ORGANIZATIONS.ORG_NAME)
                .values(name)
                .returning()
                .fetchOne()
                .getOrgId();
    }

    public void update(UUID id, String name) {
        tx(tx -> update(tx, id, name));
    }

    public void update(DSLContext tx, UUID id, String name) {
        tx.update(ORGANIZATIONS)
                .set(ORGANIZATIONS.ORG_NAME, name)
                .where(ORGANIZATIONS.ORG_ID.eq(id))
                .execute();
    }

    public List<OrganizationEntry> list() {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(ORGANIZATIONS.ORG_ID, ORGANIZATIONS.ORG_NAME)
                    .from(ORGANIZATIONS)
                    .orderBy(ORGANIZATIONS.ORG_NAME)
                    .fetch(OrganizationDao::toEntry);
        }
    }

    private static OrganizationEntry toEntry(Record2<UUID, String> r) {
        return new OrganizationEntry(r.value1(), r.value2());
    }
}
