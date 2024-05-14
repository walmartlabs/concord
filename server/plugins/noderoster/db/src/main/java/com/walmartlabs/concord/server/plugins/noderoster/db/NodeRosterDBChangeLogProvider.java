package com.walmartlabs.concord.server.plugins.noderoster.db;

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

import com.walmartlabs.concord.db.DatabaseChangeLogProvider;

@NodeRosterDB
public class NodeRosterDBChangeLogProvider implements DatabaseChangeLogProvider {

    @Override
    public String getChangeLogPath() {
        return "com/walmartlabs/concord/server/plugins/noderoster/db/liquibase.xml";
    }

    @Override
    public String toString() {
        return "noderoster-db";
    }
}
