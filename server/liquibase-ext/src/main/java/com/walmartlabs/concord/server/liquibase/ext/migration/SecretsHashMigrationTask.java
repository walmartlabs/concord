package com.walmartlabs.concord.server.liquibase.ext.migration;

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
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

public class SecretsHashMigrationTask implements CustomTaskChange {

    private static final Logger log = LoggerFactory.getLogger(SecretsHashMigrationTask.class);

    private static final int BATCH_SIZE = 100;

    private String serverPassword;

    public String getServerPassword() {
        return serverPassword;
    }

    public void setServerPassword(String serverPassword) {
        this.serverPassword = serverPassword;
    }

    @Override
    public void execute(Database database) throws CustomChangeException {
        log.info("Starting migration task for secretsHash");
        try {
            JdbcConnection con = (JdbcConnection) database.getConnection();
            byte[] serverPwd = Base64.getDecoder().decode(serverPassword);
            con.setAutoCommit(false);
            while (true) {
                try (PreparedStatement psSelect = con.prepareStatement("SELECT secret_id, secret_salt, secret_data FROM secrets WHERE hash_algorithm = ? AND encrypted_by = ? LIMIT ? FOR UPDATE")) {
                    psSelect.setString(1, HashAlgorithm.LEGACY_MD5.getName());
                    psSelect.setString(2, SecretEncryptedByType.SERVER_KEY.toString());
                    psSelect.setInt(3, BATCH_SIZE);
                    int count = 0;
                    try (ResultSet resultSet = psSelect.executeQuery();
                         PreparedStatement psUpdated = con.prepareStatement("UPDATE secrets SET secret_data = ?, hash_algorithm = ? WHERE secret_id = ?::uuid")) {
                        while (resultSet.next()) {
                            String secretId = resultSet.getString(1);
                            byte[] secretSalt = resultSet.getBytes(2);
                            byte[] encryptedData = resultSet.getBytes(3);
                            byte[] decryptedData = SecretUtils.decrypt(encryptedData, serverPwd, secretSalt, HashAlgorithm.LEGACY_MD5);
                            byte[] newlyEncryptedData = SecretUtils.encrypt(decryptedData, serverPwd, secretSalt, HashAlgorithm.SHA256);
                            psUpdated.setBytes(1, newlyEncryptedData);
                            psUpdated.setString(2, HashAlgorithm.SHA256.getName());
                            psUpdated.setObject(3, secretId);
                            psUpdated.addBatch();
                            count++;
                        }
                        if (count > 0) {
                            psUpdated.executeBatch();
                            con.commit();
                        }
                        log.info("Committing {} records with new hash algorithm", count);
                        if (count < BATCH_SIZE) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Exception in executing secretsHashMigrationTask, message : {}", e.getMessage());
            throw new CustomChangeException(e);
        }
        log.info("Successfully completed migrating password less secrets with SHA256 hash algorithm");
    }

    @Override
    public String getConfirmationMessage() {
        return null;
    }

    @Override
    public void setUp() throws SetupException {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return null;
    }
}
