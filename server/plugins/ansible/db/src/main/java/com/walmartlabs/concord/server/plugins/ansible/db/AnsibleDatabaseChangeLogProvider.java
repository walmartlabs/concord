package com.walmartlabs.concord.server.plugins.ansible.db;

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

import com.walmartlabs.concord.server.sdk.DatabaseChangeLogProvider;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class AnsibleDatabaseChangeLogProvider implements DatabaseChangeLogProvider {

    @Override
    public String getChangeLogPath() {
        return "com/walmartlabs/concord/server/plugins/ansible/db/liquibase.xml";
    }

    @Override
    public String getChangeLogTable() {
        return "ANSIBLE_DB_LOG";
    }

    @Override
    public String getLockTable() {
        return "ANSIBLE_DB_LOCK";
    }

    @Override
    public String toString() {
        return "ansible-db";
    }
}
