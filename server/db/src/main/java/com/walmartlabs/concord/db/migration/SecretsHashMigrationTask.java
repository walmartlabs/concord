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

import com.walmartlabs.concord.common.secret.HashAlgorithm;
import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.common.secret.SecretUtils;
import com.walmartlabs.ollie.config.Config;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Named
@MigrationOrder(1)
public class SecretsHashMigrationTask implements MigrationTask {


    private static final Logger log = LoggerFactory.getLogger(SecretsHashMigrationTask.class);
    private static final int MAX_RETRIES = 10;
    private static final int BATCH_SIZE = 100;

    @Inject
    @Config("secretStore.serverPassword")
    private byte[] serverPwd;

    @Override
    synchronized public void execute(DataSource dataSource) {
        log.info("Starting migration task for secretsHash");
        int retryCount = 0;
        while(retryCount <= MAX_RETRIES) {
            try(Connection con = dataSource.getConnection()) {
                while(true) {
                    try(PreparedStatement psSelect = con.prepareStatement("SELECT secret_id, secret_salt, secret_data FROM secrets WHERE hash_algorithm = ? AND encrypted_by = ? LIMIT ?")){
                        psSelect.setString(1, HashAlgorithm.LEGACY_MD5.getName());
                        psSelect.setString(2, SecretEncryptedByType.SERVER_KEY.toString());
                        psSelect.setInt(3, BATCH_SIZE);
                        ResultSet resultSet = psSelect.executeQuery();
                        int count = 0;
                        try(PreparedStatement psUpdated = con.prepareStatement("UPDATE secrets SET secret_data = ?, hash_algorithm = ? WHERE secret_id = ?")) {
                            while(resultSet.next()) {
                                String secretId = resultSet.getString(1);
                                byte[] secretSalt = resultSet.getBytes(2);
                                byte[] encryptedData = resultSet.getBytes(3);
                                PGobject uuidObject = new PGobject();
                                uuidObject.setType("uuid");
                                uuidObject.setValue(secretId);
                                byte[] decryptedData = SecretUtils.decrypt(encryptedData, serverPwd, secretSalt, HashAlgorithm.LEGACY_MD5);
                                byte[] newlyEncryptedData = SecretUtils.encrypt(decryptedData, serverPwd, secretSalt, HashAlgorithm.SHA256);
                                psUpdated.setBytes(1, newlyEncryptedData);
                                psUpdated.setString(2, HashAlgorithm.SHA256.getName());
                                psUpdated.setObject(3, uuidObject);
                                psUpdated.addBatch();
                                count++;
                            }
                            if(count > 0){
                                psUpdated.executeBatch();
                                con.commit();
                            }
                            log.info("Committing {} records with new hash algorithm", count);
                            if(count < BATCH_SIZE) {
                                break;
                            }
                        }
                    }
                }
                log.info("Successfully completed migrating password less secrets with SHA256 hash algorithm");
                break;
            } catch (Exception e) {
                if(retryCount == MAX_RETRIES) {
                    log.error("secret hash migration -> Error while running secret hash migration. giving up");
                    throw new RuntimeException(e);
                }
                log.error("Exception: {} while executing migration task for {} time.", e.getMessage(), ++retryCount);
            }
        }

    }
}
