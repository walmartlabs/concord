package com.walmartlabs.concord.server.security.secret;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.security.secret.SecretEntry;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.public_.tables.Secrets.SECRETS;

@Named
public class SecretDao extends AbstractDao {

    private final UserPermissionCleaner permissionCleaner;

    @Inject
    public SecretDao(Configuration cfg, UserPermissionCleaner permissionCleaner) {
        super(cfg);
        this.permissionCleaner = permissionCleaner;
    }

    public void insert(String id, String name, SecretType type, byte[] data) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            create.insertInto(SECRETS)
                    .columns(SECRETS.SECRET_ID, SECRETS.SECRET_NAME, SECRETS.SECRET_TYPE, SECRETS.SECRET_DATA)
                    .values(id, name, type.toString(), data)
                    .execute();
        });
    }

    public SecretDataEntry get(String id) {
        try (DSLContext create = DSL.using(cfg)) {
            return selectSecretDataEntry(create)
                    .where(SECRETS.SECRET_ID.eq(id))
                    .fetchOne(SecretDao::toDataEntry);
        }
    }

    public SecretDataEntry getByName(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return selectSecretDataEntry(create)
                    .where(SECRETS.SECRET_NAME.eq(name))
                    .fetchOne(SecretDao::toDataEntry);
        }
    }

    public String getId(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(SECRETS.SECRET_ID)
                    .from(SECRETS)
                    .where(SECRETS.SECRET_NAME.eq(name))
                    .fetchOne(SECRETS.SECRET_ID);
        }
    }

    public List<SecretEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectJoinStep<Record3<String, String, String>> query = create
                    .select(SECRETS.SECRET_ID, SECRETS.SECRET_NAME, SECRETS.SECRET_TYPE)
                    .from(SECRETS);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(r -> new SecretEntry(r.get(SECRETS.SECRET_ID),
                    r.get(SECRETS.SECRET_NAME),
                    SecretType.valueOf(r.get(SECRETS.SECRET_TYPE))));
        }
    }

    public void delete(String id) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            String name = getName(create, id);
            permissionCleaner.onSecretRemoval(create, name);

            create.deleteFrom(SECRETS)
                    .where(SECRETS.SECRET_ID.eq(id))
                    .execute();
        });
    }

    private static String getName(DSLContext create, String id) {
        return create.select(SECRETS.SECRET_NAME)
                .from(SECRETS)
                .where(SECRETS.SECRET_ID.eq(id))
                .fetchOne(SECRETS.SECRET_NAME);
    }

    private static SelectJoinStep<Record4<String, String, String, byte[]>> selectSecretDataEntry(DSLContext create) {
        return create.select(SECRETS.SECRET_ID, SECRETS.SECRET_NAME, SECRETS.SECRET_TYPE, SECRETS.SECRET_DATA)
                .from(SECRETS);
    }

    private static SecretDataEntry toDataEntry(Record4<String, String, String, byte[]> r) {
        return new SecretDataEntry(r.get(SECRETS.SECRET_ID), r.get(SECRETS.SECRET_NAME),
                SecretType.valueOf(r.get(SECRETS.SECRET_TYPE)), r.get(SECRETS.SECRET_DATA));
    }

    public static class SecretDataEntry extends SecretEntry {

        private final byte[] data;

        public SecretDataEntry(String id, String name, SecretType type, byte[] data) {
            super(id, name, type);
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }
    }
}
