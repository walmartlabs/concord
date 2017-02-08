package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.security.Permissions;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

import static com.walmartlabs.concord.server.jooq.public_.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.public_.tables.UserPermissions.USER_PERMISSIONS;

@Named
public class UserPermissionCleaner extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(UserPermissionCleaner.class);

    private static final char ESC_CHAR = '\\';

    @Inject
    public UserPermissionCleaner(Configuration cfg) {
        super(cfg);
    }

    public void onSecretRemoval(DSLContext create, String name) {
        remove(create, Permissions.SECRET_PREFIX, name);
    }

    public void onRepositoryRemoval(DSLContext create, String name) {
        remove(create, Permissions.REPOSITORY_PREFIX, name);
    }

    public void onTemplateRemoval(DSLContext create, String name) {
        remove(create, Permissions.TEMPLATE_PREFIX, name);
    }

    public void onProjectRemoval(DSLContext create, String id, String name) {
        create.select(REPOSITORIES.REPO_NAME)
                .from(REPOSITORIES)
                .where(REPOSITORIES.PROJECT_ID.eq(id))
                .forEach(r -> onRepositoryRemoval(create, r.get(REPOSITORIES.REPO_NAME)));

        remove(create, Permissions.PROJECT_PREFIX, name);
    }

    private void remove(DSLContext create, String prefix, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid entity name: " + name);
        }

        String pattern = prefix + ":%:" + escape(name);
        int i = create.deleteFrom(USER_PERMISSIONS)
                .where(USER_PERMISSIONS.PERMISSION.like(pattern).escape(ESC_CHAR))
                .execute();

        log.debug("remove ['{}'] -> removed {} '{}' permission(s)", name, i, prefix);
    }

    private static final String escape(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("_", ESC_CHAR + "_" + ESC_CHAR);
    }
}
