package com.walmartlabs.concord.db.migration;

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

import com.walmartlabs.ollie.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Named
@MigrationOrder
public class SecretsSaltMigrationTask implements MigrationTask {

    private static final Logger log = LoggerFactory.getLogger(SecretsSaltMigrationTask.class);
    private static final int MAX_RETRIES = 10;
    private static final long RETRY_DELAY = 10000;

    @Inject
    @Config("secretStore.secretStoreSalt")
    private byte[] secretSalt;

    @Override
    public void execute(DataSource dataSource) {
        log.info("Starting migration task for updating secret salt");
        int retryCount = 0;
        while(retryCount <= MAX_RETRIES) {
            try(Connection connection = dataSource.getConnection()) {
                String updateSql = "UPDATE SECRETS SET SECRET_SALT = ? WHERE SECRET_SALT IS NULL";
                try(PreparedStatement preparedStatement = connection.prepareStatement(updateSql)){
                    preparedStatement.setBytes(1, secretSalt);
                    preparedStatement.executeUpdate();
                    connection.commit();
                    break;
                }
            } catch (SQLException e) {
                if(retryCount == MAX_RETRIES) {
                    log.error("secret salt migration -> Error while running secret salt migration. giving up");
                    throw new RuntimeException(e);
                }

                log.error("Exception: {} while executing migration task for {} time.", e.getMessage(), ++retryCount);
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.info("Successfully completed migration task for secret salt");
    }

}
