package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.user.RoleEntry;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.public_.tables.RolePermissions.ROLE_PERMISSIONS;
import static com.walmartlabs.concord.server.jooq.public_.tables.Roles.ROLES;

@Named
public class RoleDao extends AbstractDao {

    @Inject
    public RoleDao(Configuration cfg) {
        super(cfg);
    }

    public Collection<String> getPermissions(Collection<String> roles) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.selectDistinct(ROLE_PERMISSIONS.PERMISSION)
                    .from(ROLE_PERMISSIONS)
                    .where(ROLE_PERMISSIONS.ROLE_NAME.in(roles))
                    .fetch(ROLE_PERMISSIONS.PERMISSION);
        }
    }

    public boolean exists(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(ROLE_PERMISSIONS)
                    .where(ROLE_PERMISSIONS.ROLE_NAME.eq(name)));
        }
    }

    public void insert(String name, String description, Collection<String> permissions) {
        tx(tx -> insert(tx, name, description, permissions));
    }

    public void insert(DSLContext tx, String name, String description, Collection<String> permissions) {
        tx.insertInto(ROLES)
                .columns(ROLES.ROLE_NAME, ROLES.ROLE_DESCRIPTION)
                .values(name, description)
                .execute();

        insertPermissions(tx, name, permissions);
    }

    public void update(String name, String description, Collection<String> permissions) {
        tx(tx -> update(tx, name, description, permissions));
    }

    public void update(DSLContext tx, String name, String description, Collection<String> permissions) {
        tx.update(ROLES)
                .set(ROLES.ROLE_DESCRIPTION, description)
                .where(ROLES.ROLE_NAME.eq(name))
                .execute();

        deletePermissions(tx, name);
        insertPermissions(tx, name, permissions);
    }

    public List<RoleEntry> list() {
        try (DSLContext tx = DSL.using(cfg)) {
            Select<Record3<String, String, String>> select = tx
                    .select(ROLES.ROLE_DESCRIPTION, ROLES.ROLE_NAME, ROLE_PERMISSIONS.PERMISSION)
                    .from(ROLES)
                    .leftOuterJoin(ROLE_PERMISSIONS).on(ROLE_PERMISSIONS.ROLE_NAME.eq(ROLES.ROLE_NAME))
                    .orderBy(ROLES.ROLE_NAME);

            List<RoleEntry> l = new ArrayList<>();

            String cName = null;
            String cDesc = null;
            Set<String> cPerms = null;

            for (Record3<String, String, String> r : select.fetch()) {
                String name = r.get(ROLES.ROLE_NAME);
                String desc = r.get(ROLES.ROLE_DESCRIPTION);
                String perm = r.get(ROLE_PERMISSIONS.PERMISSION);

                if (!name.equals(cName)) {
                    if (cName != null) {
                        l.add(new RoleEntry(cName, cDesc, cPerms));
                    }

                    cName = name;
                    cDesc = desc;
                    cPerms = new HashSet<>();
                }

                if (perm != null) {
                    cPerms.add(perm);
                }
            }
            l.add(new RoleEntry(cName, cDesc, cPerms));

            return l;
        }
    }

    public void delete(String name) {
        tx(tx -> delete(tx, name));
    }

    public void delete(DSLContext tx, String name) {
        tx.deleteFrom(ROLES)
                .where(ROLES.ROLE_NAME.eq(name))
                .execute();
    }

    private static void deletePermissions(DSLContext tx, String roleName) {
        tx.deleteFrom(ROLE_PERMISSIONS)
                .where(ROLE_PERMISSIONS.ROLE_NAME.eq(roleName))
                .execute();
    }

    private static void insertPermissions(DSLContext tx, String roleName, Collection<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }

        BatchBindStep b = tx.batch(tx.insertInto(ROLE_PERMISSIONS)
                .columns(ROLE_PERMISSIONS.ROLE_NAME, ROLE_PERMISSIONS.PERMISSION)
                .values((String) null, null));

        for (String p : permissions) {
            b.bind(roleName, p);
        }

        b.execute();
    }
}
