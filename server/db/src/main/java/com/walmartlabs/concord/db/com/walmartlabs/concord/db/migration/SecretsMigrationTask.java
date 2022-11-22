package com.walmartlabs.concord.db.com.walmartlabs.concord.db.migration;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.ollie.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@MainDB
public class SecretsMigrationTask implements MigrationTask {

    private static final Logger log = LoggerFactory.getLogger(SecretsMigrationTask.class);

    @Inject
    @Config("secretStore.secretStoreSalt")
    private byte[] secretSalt;

    @Override
    public void execute(DataSource dataSource) {
        try(Connection connection = dataSource.getConnection()) {
            String updateSql = "UPDATE SECRETS SET SECRET_SALT = ? WHERE SECRET_SALT IS NULL";
            PreparedStatement preparedStatement = connection.prepareStatement(updateSql);
            preparedStatement.setBytes(1, secretSalt);
            int result = preparedStatement.executeUpdate();
            connection.commit();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
