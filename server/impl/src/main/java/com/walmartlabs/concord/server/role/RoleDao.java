package com.walmartlabs.concord.server.role;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.jooq.tables.RoleLdapGroups;
import com.walmartlabs.concord.server.user.RoleEntry;
import org.jooq.*;

import javax.inject.Inject;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.Roles.ROLES;
import static org.jooq.impl.DSL.*;

public class RoleDao extends AbstractDao {

    @Inject
    protected RoleDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    @Override
    public void tx(Tx t) {
        super.tx(t);
    }

    public RoleEntry get(UUID id) {
        SelectConditionStep<Record1<String[]>> permissions = select(arrayAgg(PERMISSIONS.PERMISSION_NAME)).from(PERMISSIONS)
                .where(PERMISSIONS.PERMISSION_ID.in(
                        select(ROLE_PERMISSIONS.PERMISSION_ID).from(ROLE_PERMISSIONS)
                                .where(ROLE_PERMISSIONS.ROLE_ID.in(ROLES.ROLE_ID))));

        return dsl().select(ROLES.ROLE_ID, ROLES.ROLE_NAME, isnull(permissions.asField(), new String[]{}))
                .from(ROLES)
                .where(ROLES.ROLE_ID.in(id))
                .fetchOne(e -> new RoleEntry(e.value1(), e.value2(), new HashSet<>(Arrays.asList(e.value3()))));
    }

    public UUID getId(String roleName) {
        SelectConditionStep<Record1<UUID>> q = dsl().select(ROLES.ROLE_ID)
                .from(ROLES)
                .where(ROLES.ROLE_NAME.eq(roleName));

        return q.fetchOne(ROLES.ROLE_ID);
    }

    public UUID insert(String roleName, Set<String> permissions) {
        return txResult(tx -> insert(tx, roleName, permissions));
    }

    public UUID insert(DSLContext tx, String roleName, Set<String> permissions) {
        UUID roleId = tx.insertInto(ROLES)
                .columns(ROLES.ROLE_NAME)
                .values(value(roleName))
                .returning(ROLES.ROLE_ID)
                .fetchOne().getRoleId();

        if (permissions != null) {
            tx.insertInto(ROLE_PERMISSIONS).select(
                    select(
                            value(roleId).as(ROLE_PERMISSIONS.ROLE_ID),
                            PERMISSIONS.PERMISSION_ID.as(ROLE_PERMISSIONS.PERMISSION_ID)
                    ).from(PERMISSIONS).where(PERMISSIONS.PERMISSION_NAME.in(permissions)))
                    .execute();
        }

        return roleId;
    }

    public void update(UUID roleId, String name, Set<String> permissions) {
        tx(tx -> {

            tx.update(ROLES)
                    .set(ROLES.ROLE_NAME, name)
                    .where(ROLES.ROLE_ID.eq(roleId))
                    .execute();

            if (permissions != null) {
                tx.deleteFrom(ROLE_PERMISSIONS).where(ROLE_PERMISSIONS.ROLE_ID.eq(roleId)).execute();

                tx.insertInto(ROLE_PERMISSIONS).select(
                        select(
                                value(roleId).as(ROLE_PERMISSIONS.ROLE_ID),
                                PERMISSIONS.PERMISSION_ID.as(ROLE_PERMISSIONS.PERMISSION_ID)
                        ).from(PERMISSIONS).where(PERMISSIONS.PERMISSION_NAME.in(permissions)))
                        .execute();
            }
        });
    }

    public void delete(UUID roleId) {
        tx(tx -> tx.deleteFrom(ROLES)
                .where(ROLES.ROLE_ID.eq(roleId))
                .execute());
    }

    public List<RoleEntry> list() {
        SelectConditionStep<Record1<String[]>> permissions = select(arrayAgg(PERMISSIONS.PERMISSION_NAME)).from(PERMISSIONS)
                .where(PERMISSIONS.PERMISSION_ID.in(
                        select(ROLE_PERMISSIONS.PERMISSION_ID).from(ROLE_PERMISSIONS)
                                .where(ROLE_PERMISSIONS.ROLE_ID.eq(ROLES.ROLE_ID))));


        return dsl().select(ROLES.ROLE_ID, ROLES.ROLE_NAME, isnull(permissions.asField(), new String[]{}))
                .from(ROLES)
                .fetch(RoleDao::toEntry);
    }

    public void upsertLdapGroup(DSLContext tx, UUID roleId, String ldapGroup) {
        tx.insertInto(ROLE_LDAP_GROUPS)
                .columns(ROLE_LDAP_GROUPS.ROLE_ID, ROLE_LDAP_GROUPS.LDAP_GROUP)
                .values(roleId, ldapGroup)
                .onConflict(ROLE_LDAP_GROUPS.ROLE_ID, ROLE_LDAP_GROUPS.LDAP_GROUP)
                .doNothing()
                .execute();
    }
    
    public void removeLdapGroups(DSLContext tx, UUID roleId) {
        tx.deleteFrom(ROLE_LDAP_GROUPS)
                .where(ROLE_LDAP_GROUPS.ROLE_ID.eq(roleId))
                .execute();
    }

    public List<String> listLdapGroups(UUID roleId) {
        RoleLdapGroups r = ROLE_LDAP_GROUPS.as("r");
        return txResult(tx -> tx.select(r.LDAP_GROUP)
                .from(r)
                .where(r.ROLE_ID.eq(roleId))
                .orderBy(r.LDAP_GROUP)
                .fetch(r.LDAP_GROUP));
    }
    
    private static RoleEntry toEntry(Record3<UUID, String, String[]> e) {
        return new RoleEntry(e.value1(), e.value2(), new HashSet<>(Arrays.asList(e.value3())));
    }

}
