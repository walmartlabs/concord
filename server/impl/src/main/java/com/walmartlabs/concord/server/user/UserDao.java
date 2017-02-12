package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.security.User;
import org.jooq.BatchBindStep;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.walmartlabs.concord.server.jooq.public_.tables.UserPermissions.USER_PERMISSIONS;
import static com.walmartlabs.concord.server.jooq.public_.tables.Users.USERS;

@Named
public class UserDao extends AbstractDao {

    @Inject
    public UserDao(Configuration cfg) {
        super(cfg);
    }

    public void insert(String id, String username, Set<String> permissions) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            create.insertInto(USERS)
                    .columns(USERS.USER_ID, USERS.USERNAME)
                    .values(id, username)
                    .execute();

            insertPermissions(create, id, permissions);
        });
    }

    public void delete(String id) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            create.deleteFrom(USERS)
                    .where(USERS.USER_ID.eq(id))
                    .execute();
        });
    }

    public void update(String id, Set<String> permissions) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            deletePermissions(create, id);
            insertPermissions(create, id, permissions);
        });
    }

    public User get(String id) {
        try (DSLContext create = DSL.using(cfg)) {
            Record2<String, String> r = create
                    .select(USERS.USER_ID,
                            USERS.USERNAME)
                    .from(USERS)
                    .where(USERS.USER_ID.eq(id))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            // TODO join?
            List<String> perms = create.select(USER_PERMISSIONS.PERMISSION)
                    .from(USER_PERMISSIONS)
                    .where(USER_PERMISSIONS.USER_ID.eq(id))
                    .fetchInto(String.class);

            return new User(r.get(USERS.USER_ID), r.get(USERS.USERNAME), new HashSet<>(perms));
        }
    }

    public boolean exists(String username) {
        try (DSLContext create = DSL.using(cfg)) {
            int cnt = create.fetchCount(create.selectFrom(USERS)
                    .where(USERS.USERNAME.eq(username)));

            return cnt > 0;
        }
    }

    public boolean existsById(String id) {
        try (DSLContext create = DSL.using(cfg)) {
            int cnt = create.fetchCount(create.selectFrom(USERS)
                    .where(USERS.USER_ID.eq(id)));

            return cnt > 0;
        }
    }

    private static void insertPermissions(DSLContext create, String userId, Set<String> permissions) {
        if (permissions != null && !permissions.isEmpty()) {
            BatchBindStep b = create.batch(create.insertInto(USER_PERMISSIONS)
                    .columns(USER_PERMISSIONS.USER_ID, USER_PERMISSIONS.PERMISSION)
                    .values((String) null, null));

            for (String p : permissions) {
                b.bind(userId, p);
            }

            b.execute();
        }
    }

    private static void deletePermissions(DSLContext create, String userId) {
        create.deleteFrom(USER_PERMISSIONS).where(USER_PERMISSIONS.USER_ID.eq(userId)).execute();
    }
}
