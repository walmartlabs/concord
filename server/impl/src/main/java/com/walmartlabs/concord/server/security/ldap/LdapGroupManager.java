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

import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.cfg.LdapGroupSyncConfiguration;
import org.jooq.Field;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Named
public class LdapGroupManager {

    private final LdapGroupSyncConfiguration syncCfg;
    private final LdapGroupDao groupDao;

    @Inject
    public LdapGroupManager(LdapGroupSyncConfiguration syncCfg, LdapGroupDao groupDao) {
        this.syncCfg = syncCfg;
        this.groupDao = groupDao;
    }

    public void cacheLdapGroupsIfNeeded(UUID userId, Set<String> groups) {
        Field<OffsetDateTime> cutoff = PgUtils.nowMinus(syncCfg.getMinAgeLogin());
        groupDao.updateIfNeeded(userId, groups, cutoff);
    }
}
