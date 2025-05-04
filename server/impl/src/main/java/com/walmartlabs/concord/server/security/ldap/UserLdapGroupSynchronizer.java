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
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.audit.ActionSource;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.cfg.LdapGroupSyncConfiguration;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.Configuration;
import org.jooq.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.USERS;
import static org.jooq.impl.DSL.*;

/**
 * Responsible for AD/LDAP group synchronization and enabling/disabling users
 * based on whether they have active groups or not.
 */
public class UserLdapGroupSynchronizer implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(UserLdapGroupSynchronizer.class);

    private static final String TASK_NAME = "user-ldap-group-sync";
    private final Dao dao;
    private final LdapGroupSyncConfiguration cfg;
    private final LdapManager ldapManager;
    private final LdapGroupDao ldapGroupsDao;
    private final UserManager userManager;

    @Inject
    public UserLdapGroupSynchronizer(Dao dao, LdapGroupSyncConfiguration cfg,
                                     LdapManager ldapManager,
                                     LdapGroupDao ldapGroupsDao,
                                     UserManager userManager) {

        this.dao = dao;
        this.cfg = cfg;
        this.ldapManager = ldapManager;
        this.userManager = userManager;
        this.ldapGroupsDao = ldapGroupsDao;
    }

    @Override
    public String getId() {
        return TASK_NAME;
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getInterval().getSeconds();
    }

    @Override
    public void performTask() {
        Field<OffsetDateTime> cutoff = PgUtils.nowMinus(cfg.getMinAgeSync());
        Field<OffsetDateTime> disabledAge = cfg.getDisabledAge() != null ? PgUtils.nowMinus(cfg.getDisabledAge()) : null;

        long usersCount = 0;
        List<UserItem> users;
        do {
            users = dao.list(cfg.getFetchLimit(), cutoff, disabledAge);
            users.forEach(this::processUser);
            usersCount += users.size();
        } while (users.size() >= cfg.getFetchLimit());

        log.info("performTask -> done, {} user(s) synchronized", usersCount);
    }

    private void processUser(UserItem u) {
        try {
            Set<String> groups = ldapManager.getGroups(u.username, u.domain);
            if (groups == null) {
                if (u.expired) {
                    deleteUser(u.userId);
                } else {
                    ldapGroupsDao.update(u.userId, Collections.emptySet());
                    disableUser(u.userId);
                }
            } else if (!u.permanentlyDisabled) {
                enableUser(u.userId);
                ldapGroupsDao.update(u.userId, groups);
            }
        } catch (Exception e) {
            log.error("processUser ['{}'] -> error", u.username, e);
        }
    }

    private void enableUser(UUID userId) {
        AuditLog.withActionSource(ActionSource.SYSTEM, Collections.singletonMap("task", TASK_NAME),
                () -> userManager.enable(userId));
    }

    private void disableUser(UUID userId) {
        AuditLog.withActionSource(ActionSource.SYSTEM, Collections.singletonMap("task", TASK_NAME),
                () -> userManager.disable(userId));
    }

    private void deleteUser(UUID userId) {
        AuditLog.withActionSource(ActionSource.SYSTEM, Collections.singletonMap("task", TASK_NAME),
                () -> userManager.delete(userId));
    }

    public static final class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        List<UserItem> list(int limit, Field<OffsetDateTime> cutoff, Field<OffsetDateTime> disabledAge) {
            Field<Boolean> expiredFiled = disabledAge == null ? inline(false) : field(nvl(USERS.DISABLED_DATE, currentOffsetDateTime()).lessThan(disabledAge));
            return txResult(tx -> tx.select(USERS.USER_ID, USERS.USERNAME, USERS.DOMAIN, USERS.IS_PERMANENTLY_DISABLED, expiredFiled)
                    .from(USERS)
                    .where(USERS.USER_TYPE.eq(UserType.LDAP.name()))
                    .and(USERS.LAST_GROUP_SYNC_DT.isNull().or(USERS.LAST_GROUP_SYNC_DT.lessThan(cutoff)))
                    .limit(limit)
                    .fetch(r -> new UserItem(r.value1(), r.value2(), r.value3(), r.value4(), r.value5())));
        }
    }

    private static class UserItem {

        private final UUID userId;
        private final String username;
        private final String domain;
        private final boolean permanentlyDisabled;
        private final boolean expired;

        private UserItem(UUID userId, String username, String domain, boolean expired, boolean permanentlyDisabled) {
            this.userId = userId;
            this.username = username;
            this.domain = domain;
            this.expired = expired;
            this.permanentlyDisabled = permanentlyDisabled;
        }
    }
}
