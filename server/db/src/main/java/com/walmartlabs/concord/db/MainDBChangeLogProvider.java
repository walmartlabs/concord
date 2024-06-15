package com.walmartlabs.concord.db;

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

@MainDB
public class MainDBChangeLogProvider implements DatabaseChangeLogProvider {

    @Override
    public String getChangeLogPath() {
        return "com/walmartlabs/concord/server/db/liquibase.xml";
    }

    @Override
    public int order() {
        // we expect the server's DB to be migrated first
        return 0;
    }

    @Override
    public String toString() {
        return "server-db";
    }
}
