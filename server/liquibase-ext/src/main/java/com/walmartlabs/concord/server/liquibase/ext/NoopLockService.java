package com.walmartlabs.concord.server.liquibase.ext;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.lockservice.LockService;

public class NoopLockService implements LockService {

    @Override
    public boolean supports(Database database) {
        return true;
    }

    @Override
    public void init() throws DatabaseException {
    }

    @Override
    public void setDatabase(Database database) {
    }

    @Override
    public void setChangeLogLockWaitTime(long changeLogLockWaitTime) {
    }

    @Override
    public void setChangeLogLockRecheckTime(long changeLogLockRecheckTime) {
    }

    @Override
    public boolean hasChangeLogLock() {
        return false;
    }

    @Override
    public void waitForLock() {
    }

    @Override
    public boolean acquireLock() {
        return false;
    }

    @Override
    public void releaseLock() {
    }

    @Override
    public DatabaseChangeLogLock[] listLocks() {
        return new DatabaseChangeLogLock[0];
    }

    @Override
    public void forceReleaseLock() {
    }

    @Override
    public void reset() {
    }

    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public void destroy() throws DatabaseException {

    }
}
