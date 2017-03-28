package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.common.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;

import static com.walmartlabs.concord.server.jooq.public_.tables.RolePermissions.ROLE_PERMISSIONS;

@Named
public class RoleDao extends AbstractDao {

    @Inject
    public RoleDao(Configuration cfg) {
        super(cfg);
    }

    public Collection<String> getPermissions(Collection<String> roles) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(ROLE_PERMISSIONS.PERMISSION)
                    .from(ROLE_PERMISSIONS)
                    .where(ROLE_PERMISSIONS.ROLE_NAME.in(roles))
                    .fetch(ROLE_PERMISSIONS.PERMISSION);
        }
    }
}
