package com.walmartlabs.concord.server.security.secret;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.security.secret.SecretEntry;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.ByteArrayInputStream;
import java.sql.PreparedStatement;
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

    public void insert(String name, SecretType type, byte[] data) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            // TODO workaround for the 'column is of type oid but expression is of type bytea' problem
            String sql = create.insertInto(SECRETS)
                    .columns(SECRETS.SECRET_NAME, SECRETS.SECRET_TYPE, SECRETS.SECRET_DATA)
                    .values((String) null, null, null)
                    .getSQL();

            create.connection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, name);
                    ps.setString(2, type.toString());
                    ps.setBinaryStream(3, new ByteArrayInputStream(data));

                    ps.execute();
                }
            });
        });
    }

    public SecretDataEntry get(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return selectSecretDataEntry(create)
                    .where(SECRETS.SECRET_NAME.eq(name))
                    .fetchOne(SecretDao::toDataEntry);
        }
    }

    public List<SecretEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectJoinStep<Record2<String, String>> query = create
                    .select(SECRETS.SECRET_NAME, SECRETS.SECRET_TYPE)
                    .from(SECRETS);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(r -> new SecretEntry(r.get(SECRETS.SECRET_NAME),
                    SecretType.valueOf(r.get(SECRETS.SECRET_TYPE))));
        }
    }

    public void delete(String name) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            permissionCleaner.onSecretRemoval(create, name);
            create.deleteFrom(SECRETS)
                    .where(SECRETS.SECRET_NAME.eq(name))
                    .execute();
        });
    }

    public boolean exists(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.fetchExists(create.selectFrom(SECRETS)
                    .where(SECRETS.SECRET_NAME.eq(name)));
        }
    }

    private static SelectJoinStep<Record3<String, String, byte[]>> selectSecretDataEntry(DSLContext create) {
        return create.select(SECRETS.SECRET_NAME, SECRETS.SECRET_TYPE, SECRETS.SECRET_DATA)
                .from(SECRETS);
    }

    private static SecretDataEntry toDataEntry(Record3<String, String, byte[]> r) {
        return new SecretDataEntry(r.get(SECRETS.SECRET_NAME), SecretType.valueOf(r.get(SECRETS.SECRET_TYPE)), r.get(SECRETS.SECRET_DATA));
    }

    public static class SecretDataEntry extends SecretEntry {

        private final byte[] data;

        public SecretDataEntry(String name, SecretType type, byte[] data) {
            super(name, type);
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }
    }
}
