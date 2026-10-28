package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.server.cfg.LdapGroupSyncConfiguration;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Refreshes LDAP source bindings without changing account status. */
public class UserLdapGroupSynchronizer implements ScheduledTask {
    private static final Logger log = LoggerFactory.getLogger(UserLdapGroupSynchronizer.class);
    private static final String TASK_NAME = "user-ldap-group-sync";
    private final Dao dao;
    private final LdapGroupSyncConfiguration cfg;
    private final AuthorizationService authorizationService;

    @Inject
    public UserLdapGroupSynchronizer(Dao dao, LdapGroupSyncConfiguration cfg, AuthorizationService authorizationService) {
        this.dao = dao;
        this.cfg = cfg;
        this.authorizationService = authorizationService;
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
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(cfg.getMinAgeSync());
        UUID after = null;
        long count = 0;
        List<UUID> users;
        do {
            users = dao.list(cfg.getFetchLimit(), cutoff, after);
            for (UUID userId : users) {
                try {
                    authorizationService.refresh(userId, "ldap", true);
                } catch (Exception e) {
                    // A failure is recorded by the authorization service; continue the batch.
                    log.warn("LDAP source refresh failed for user {}", userId, e);
                }
                after = userId;
            }
            count += users.size();
        } while (users.size() >= cfg.getFetchLimit() && !users.isEmpty());
        log.info("performTask -> done, {} user(s) synchronized", count);
    }

    public static class Dao extends AbstractDao {
        @Inject
        public Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        List<UUID> list(int limit, OffsetDateTime cutoff, UUID after) {
            return txResult(tx -> tx.fetch("select b.user_id from user_authorization_bindings b " +
                            "left join user_authorization_state s on s.user_id=b.user_id and s.source_id=b.source_id " +
                            "and s.binding_id=b.binding_id " +
                            "where b.source_id='ldap' and (s.observed_at is null or s.observed_at < ?) " +
                            (after == null ? "" : "and b.user_id > ? ") +
                            "order by b.user_id limit ?",
                    after == null ? new Object[]{cutoff, limit} : new Object[]{cutoff, after, limit})
                    .map(r -> r.get("user_id", UUID.class)));
        }
    }
}
