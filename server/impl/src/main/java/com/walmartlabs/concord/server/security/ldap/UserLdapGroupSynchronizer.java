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
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.Configuration;
import org.jooq.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.USERS;
import static com.walmartlabs.concord.server.jooq.Tables.USER_TEAMS;
import static org.jooq.impl.DSL.*;

/**
 * Responsible for AD/LDAP group synchronization and enabling/disabling users
 * based on whether they have active groups or not.
 */
public class UserLdapGroupSynchronizer implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(UserLdapGroupSynchronizer.class);

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
        return "user-ldap-group-sync";
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getInterval().getSeconds();
    }

    @Override
    public void performTask() {
        Field<OffsetDateTime> cutoff = PgUtils.nowMinus(cfg.getMinAgeSync());
        Field<OffsetDateTime> disabledAge = cfg.getDisabledAge() != null ? PgUtils.nowMinus(cfg.getDisabledAge()) : null;

        List<UserItem> concordOrgOwners = new ArrayList<>();

        long usersCount = 0;
        List<UserItem> users;
        do {
            users = dao.list(cfg.getFetchLimit(), cutoff, disabledAge);
            users.forEach(user -> {
                processUser(user, concordOrgOwners);
            });
            usersCount += users.size();
        } while (users.size() >= cfg.getFetchLimit());

        //TODO: Check concordOrgOwners against all owners in DB. Send discrepancy report on slack or email
        checkOrgOwnerDiscrepancies(users, dao.listOrgOwners());

        log.info("performTask -> done, {} user(s) synchronized", usersCount);
    }

    private void processUser(UserItem u, List<UserItem> concordOrgOwners) {
        try {
            Set<String> groups = ldapManager.getGroups(u.username, u.domain);
            if (groups == null) {
                if (u.expired) {
                    deleteUser(u.userId);
                } else {
                    ldapGroupsDao.update(u.userId, Collections.emptySet());
                    disableUser(u.userId);
                }
            } else {
                enableUser(u.userId);
                ldapGroupsDao.update(u.userId, groups);
                if(groups.contains(cfg.getConcordOrgOwnersGroup())) {
                    concordOrgOwners.add(u);
                }
            }
        } catch (Exception e) {
            log.error("processUser ['{}'] -> error", u.username, e);
        }
    }

    private void enableUser(UUID userId) {
        AuditLog.withActionSource(ActionSource.SYSTEM, Collections.singletonMap("task", "user-ldap-group-sync"),
                () -> userManager.enable(userId));
    }

    private void disableUser(UUID userId) {
        AuditLog.withActionSource(ActionSource.SYSTEM, Collections.singletonMap("task", "user-ldap-group-sync"),
                () -> userManager.disable(userId));
    }

    private void deleteUser(UUID userId) {
        AuditLog.withActionSource(ActionSource.SYSTEM, Collections.singletonMap("task", "user-ldap-group-sync"),
                () -> userManager.delete(userId));
    }

    @Named
    private static final class Dao extends AbstractDao {

        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public List<UserItem> list(int limit, Field<OffsetDateTime> cutoff, Field<OffsetDateTime> disabledAge) {
            Field<Boolean> expiredFiled = disabledAge == null ? inline(false) : field(nvl(USERS.DISABLED_DATE, currentOffsetDateTime()).lessThan(disabledAge));
            return txResult(tx -> tx.select(USERS.USER_ID, USERS.USERNAME, USERS.DOMAIN, expiredFiled)
                    .from(USERS)
                    .where(USERS.USER_TYPE.eq(UserType.LDAP.name()))
                    .and(USERS.LAST_GROUP_SYNC_DT.isNull().or(USERS.LAST_GROUP_SYNC_DT.lessThan(cutoff)))
                    .limit(limit)
                    .fetch(r -> new UserItem(r.value1(), r.value2(), r.value3(), r.value4())));
        }

         public List<UserItem> listOrgOwners() {
            // Expired users already handled at this stage
            return txResult(tx -> tx.select(USERS.USER_ID, USERS.USERNAME, USERS.DOMAIN)
                    .from(USER_TEAMS.join(USERS).on(USER_TEAMS.USER_ID.eq(USERS.USER_ID)))
                    .where(USER_TEAMS.TEAM_ROLE.eq(TeamRole.OWNER.toString()))
                    .fetch(r -> new UserItem(r.value1(), r.value2(), r.value3(), false)));
        }
    }

    private void checkOrgOwnerDiscrepancies(List<UserItem> ldapGroupUsers, List<UserItem> ownerRoleUsers) {
        List<UserItem> diffUsersOnlyInLdapGroup = ldapGroupUsers.stream()
                .filter(item -> !ownerRoleUsers.contains(item))
                .toList();

        List<UserItem> diffUsersWithOwnerRoleAndNotInLdap = ownerRoleUsers.stream()
                .filter(item -> !ldapGroupUsers.contains(item))
                .toList();


        log.info("checkOrgOwnerDiscrepancies -> done, {} user(s) only in ldapGroup not registered as owners", diffUsersOnlyInLdapGroup.stream().map(Object::toString).collect(Collectors.joining(", ")));
        log.info("checkOrgOwnerDiscrepancies -> done, {} user(s) only registered as owners not in ldapGroup", ldapGroupUsers.stream().map(Object::toString).collect(Collectors.joining(", ")));

    }

    private static class UserItem implements Comparable<UserItem> {

        private final UUID userId;
        private final String username;
        private final String domain;
        private final boolean expired;

        private UserItem(UUID userId, String username, String domain, boolean expired) {
            this.userId = userId;
            this.username = username;
            this.domain = domain;
            this.expired = expired;
        }

        @Override
        public int compareTo(UserItem o) {
            if(userId==o.userId)
                return 0;
            else
                return userId.compareTo(o.userId);
        }
    }
}
