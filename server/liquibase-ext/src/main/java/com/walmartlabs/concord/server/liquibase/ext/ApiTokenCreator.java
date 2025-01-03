package com.walmartlabs.concord.server.liquibase.ext;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import liquibase.change.custom.CustomSqlChange;
import liquibase.change.custom.CustomSqlRollback;
import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.DeleteStatement;
import liquibase.statement.core.InsertStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates a new API token for a given user.
 */
public class ApiTokenCreator implements CustomSqlChange, CustomSqlRollback {

    private static final Logger log = LoggerFactory.getLogger(ApiTokenCreator.class);

    private static final String DEFAULT_KEY_NAME = "autogenerated";

    private String token;
    private String userId;
    private String username;
    private String skip;
    private String keyName;

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setSkip(String skip) {
        this.skip = skip;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    @Override
    public SqlStatement[] generateStatements(Database database) {
        if (this.token == null) {
            log.info("API token generation is disabled for userId={}, username={}, skipping...", userId, username);
            return new SqlStatement[0];
        }

        return new SqlStatement[]{
                new InsertStatement(null, null, "API_KEYS")
                        .addColumnValue("API_KEY", hash(token))
                        .addColumnValue("USER_ID", userId)
                        .addColumnValue("KEY_NAME", getKeyName())
        };
    }

    @Override
    public SqlStatement[] generateRollbackStatements(Database database) {
        if (this.token == null) {
            return new SqlStatement[0];
        }

        if (userId != null) {
            return new SqlStatement[]{
                    new DeleteStatement(null, null, "API_KEYS")
                            .setWhere("USER_ID=? and KEY_NAME=?")
                            .addWhereParameter(userId)
                            .addWhereParameter(getKeyName())
            };
        } else {
            return new SqlStatement[]{
                    new DeleteStatement(null, null, "API_KEYS")
                            .setWhere("KEY_NAME=?")
                            .addWhereParameter(getKeyName())
            };
        }
    }

    @Override
    public String getConfirmationMessage() {
        if (this.token == null) {
            return null;
        }

        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println();
        if (username == null) {
            System.out.println("API token created (without a user): " + token);
        } else {
            System.out.println("API token created for user '" + username + "': " + token);
        }
        System.out.println();
        System.out.println("(don't forget to remove it in production)");
        System.out.println();
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

        return null;
    }

    @Override
    public void setUp() {
        if (this.skip.equals("true")) {
            this.token = null;
            return;
        }

        if (this.token == null || this.token.trim().isEmpty()) {
            this.token = newApiKey();
        }
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return null;
    }

    private String getKeyName() {
        if (this.keyName != null) {
            return this.keyName;
        }
        return DEFAULT_KEY_NAME;
    }

    private static String newApiKey() {
        try {
            byte[] ab = new byte[16];
            SecureRandom.getInstance("NativePRNGNonBlocking").nextBytes(ab);

            Base64.Encoder e = Base64.getEncoder().withoutPadding();
            return e.encodeToString(ab);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String hash(String s) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        byte[] ab = Base64.getDecoder().decode(s);
        ab = md.digest(ab);

        return Base64.getEncoder().withoutPadding().encodeToString(ab);
    }
}
