package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.security.Permissions;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;

import static com.walmartlabs.concord.server.jooq.tables.UserPermissions.USER_PERMISSIONS;

@Named
public class UserPermissionCleaner extends AbstractDao {

    private static final char ESC_CHAR = '\\';

    @Inject
    public UserPermissionCleaner(Configuration cfg) {
        super(cfg);
    }

    public void onSecretRemoval(DSLContext tx, String name) {
        remove(tx, Permissions.SECRET_PREFIX, name);
    }

    public void onTemplateRemoval(DSLContext tx, String name) {
        remove(tx, Permissions.TEMPLATE_PREFIX, name);
    }

    public void onProjectRemoval(DSLContext tx, String name) {
        remove(tx, Permissions.PROJECT_PREFIX, name);
    }

    private void remove(DSLContext tx, String prefix, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid entity name: " + name);
        }

        String pattern = prefix + ":%:" + escape(name);
        tx.deleteFrom(USER_PERMISSIONS)
                .where(USER_PERMISSIONS.PERMISSION.like(pattern).escape(ESC_CHAR))
                .execute();
    }

    private static String escape(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("_", ESC_CHAR + "_" + ESC_CHAR);
    }
}
