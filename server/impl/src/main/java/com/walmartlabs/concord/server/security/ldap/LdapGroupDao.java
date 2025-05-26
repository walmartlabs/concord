package com.walmartlabs.concord.server.security.ldap;

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
import org.jooq.*;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.USERS;
import static com.walmartlabs.concord.server.jooq.Tables.USER_LDAP_GROUPS;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.value;

public class LdapGroupDao extends AbstractDao {

    @Inject
    public LdapGroupDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    public void updateIfNeeded(UUID userId, Set<String> groups, Field<OffsetDateTime> cutOff) {
        tx(tx -> {
            Record1<Integer> r = tx.select(value(1)).from(USERS).where(USERS.USER_ID.eq(userId)
                    .and(USERS.LAST_GROUP_SYNC_DT.isNull()
                            .or(USERS.LAST_GROUP_SYNC_DT.lessThan(cutOff))))
                    .forUpdate()
                    .fetchOne();

            if (r == null) {
                return;
            }

            updateGroups(tx, userId, groups);
            updateLastSyncTimestamp(tx, userId);
        });
    }

    public void update(UUID userId, Set<String> groups) {
        tx(tx -> {
            updateGroups(tx, userId, groups);
            updateLastSyncTimestamp(tx, userId);
        });
    }

    private void updateGroups(DSLContext tx, UUID userId, Set<String> groups) {
        tx.deleteFrom(USER_LDAP_GROUPS).where(USER_LDAP_GROUPS.USER_ID.eq(userId))
                .execute();

        if (groups.isEmpty()) {
            return;
        }

        BatchBindStep q = tx.batch(tx.insertInto(USER_LDAP_GROUPS, USER_LDAP_GROUPS.USER_ID, USER_LDAP_GROUPS.LDAP_GROUP)
                .values((UUID) null, null));

        for (String g : groups) {
            q.bind(value(userId), value(g));
        }

        q.execute();
    }
    
    private void updateLastSyncTimestamp(DSLContext tx, UUID userId) {
        tx.update(USERS).set(USERS.LAST_GROUP_SYNC_DT, currentOffsetDateTime())
                .where(USERS.USER_ID.eq(userId))
                .execute();
    }
}
