package com.walmartlabs.concord.server.process.locks;

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
import com.walmartlabs.concord.server.jooq.enums.ProcessLockScope;
import com.walmartlabs.concord.server.jooq.tables.ProcessLocks;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessLocksRecord;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SelectConditionStep;

import javax.inject.Inject;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_LOCKS;

public class ProcessLocksDao extends AbstractDao {

    @Inject
    protected ProcessLocksDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    public LockEntry tryLock(ProcessKey processKey, UUID orgId, UUID projectId, ProcessLockScope scope, String lockName) {
        while (true) {
            boolean locked = insert(processKey, orgId, projectId, scope, lockName);
            if (locked) {
                return LockEntry.builder()
                        .instanceId(processKey.getInstanceId())
                        .orgId(orgId)
                        .projectId(projectId)
                        .scope(scope)
                        .name(lockName)
                        .build();
            } else {
                LockEntry e = get(orgId, projectId, scope, lockName);
                if (e != null) {
                    return e;
                }
            }
        }
    }

    public LockEntry get(UUID orgId, UUID projectId, ProcessLockScope scope, String lockName) {
        return txResult(tx -> get(tx, orgId, projectId, scope, lockName));
    }

    public boolean insert(ProcessKey processKey, UUID orgId, UUID projectId, ProcessLockScope scope, String lockName) {
        return txResult(tx -> insert(tx, processKey, orgId, projectId, scope, lockName));
    }

    public void delete(UUID instanceId, UUID orgId, UUID projectId, ProcessLockScope scope, String lockName) {
        tx(tx -> delete(tx, instanceId, orgId, projectId, scope, lockName));
    }

    private boolean insert(DSLContext tx, ProcessKey processKey, UUID orgId, UUID projectId, ProcessLockScope scope, String lockName) {
        ProcessLocks l = PROCESS_LOCKS.as("l");
        return tx.insertInto(l, l.INSTANCE_ID, l.ORG_ID, l.PROJECT_ID, l.LOCK_SCOPE, l.LOCK_NAME)
                .values(processKey.getInstanceId(), orgId, projectId, scope, lockName)
                .onConflictDoNothing()
                .execute() == 1;
    }

    private LockEntry get(DSLContext tx, UUID orgId, UUID projectId, ProcessLockScope scope, String lockName) {
        ProcessLocks l = PROCESS_LOCKS.as("l");
        SelectConditionStep<ProcessLocksRecord> q = tx.selectFrom(l)
                .where(l.LOCK_NAME.eq(lockName)
                        .and(l.LOCK_SCOPE.eq(scope)));

        switch (scope) {
            case ORG:
                q.and(l.ORG_ID.eq(orgId));
                break;
            case PROJECT:
                q.and(l.PROJECT_ID.eq(projectId));
                break;
            default:
                throw new IllegalArgumentException("unknown lock scope: " + scope);
        }

        return q.fetchOne(r -> LockEntry.builder()
                .instanceId(r.getInstanceId())
                .orgId(r.getOrgId())
                .projectId(r.getProjectId())
                .scope(r.getLockScope())
                .name(r.getLockName())
                .build());
    }

    private void delete(DSLContext tx, UUID instanceId, UUID orgId, UUID projectId, ProcessLockScope scope, String lockName) {
        ProcessLocks l = PROCESS_LOCKS.as("l");
        tx.deleteFrom(l)
                .where(l.INSTANCE_ID.eq(instanceId)
                        .and(l.ORG_ID.eq(orgId))
                        .and(l.PROJECT_ID.eq(projectId))
                        .and(l.LOCK_SCOPE.eq(scope))
                        .and(l.LOCK_NAME.eq(lockName)))
                .execute();
    }
}
