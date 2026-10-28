package com.walmartlabs.concord.server.liquibase.ext.migration;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** A single Liquibase transaction owns the snapshot, identities and imported grants. */
public class LegacySecurityMigration implements CustomTaskChange {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void execute(Database database) throws CustomChangeException {
        try {
            Connection connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
            Map<String, List<UUID>> ldapSubjects = new LinkedHashMap<>();
            try (Statement s = connection.createStatement();
                 ResultSet rs = s.executeQuery("SELECT USER_ID, USERNAME, DOMAIN FROM USERS WHERE USER_TYPE = 'LDAP' ORDER BY USER_ID")) {
                while (rs.next()) {
                    UUID id = (UUID) rs.getObject(1);
                    String domain = rs.getString(3);
                    String subject = JSON.writeValueAsString(Arrays.asList(rs.getString(2).toLowerCase(Locale.ROOT),
                            domain == null ? null : domain.toLowerCase(Locale.ROOT)));
                    ldapSubjects.computeIfAbsent(subject, ignored -> new ArrayList<>()).add(id);
                }
            }
            List<String> collisions = new ArrayList<>();
            ldapSubjects.forEach((subject, ids) -> {
                if (ids.size() > 1) {
                    collisions.add(subject + ": " + ids);
                }
            });
            if (!collisions.isEmpty()) {
                throw new CustomChangeException("LDAP identity collisions: " + String.join("; ", collisions));
            }

            execute(connection, "INSERT INTO LEGACY_USER_ENROLLMENT (USER_ID, USERNAME, DOMAIN, USER_TYPE) SELECT USER_ID, USERNAME, DOMAIN, USER_TYPE FROM USERS");
            execute(connection, "INSERT INTO AUTHORIZATION_SOURCES (SOURCE_ID, IS_ACTIVE) VALUES ('ldap', false)");
            execute(connection, "UPDATE USERS SET PROFILE_PROVIDER_ID = CASE WHEN USER_TYPE IN ('LDAP', 'SSO') THEN 'ldap' ELSE 'local' END");

            try (PreparedStatement identity = connection.prepareStatement("INSERT INTO USER_IDENTITIES (IDENTITY_ID, USER_ID, PROVIDER_ID, AUTHORITY, SUBJECT, IS_ACTIVE) VALUES (?, ?, ?, '', ?, true)");
                 PreparedStatement adoption = connection.prepareStatement("INSERT INTO LEGACY_IDENTITY_ADOPTIONS (USER_ID, PROVIDER_ID, AUTHORITY, SUBJECT, ADOPTED_AT) VALUES (?, 'ldap', '', ?, current_timestamp)");
                 PreparedStatement profile = connection.prepareStatement("UPDATE USERS SET PROFILE_IDENTITY_ID = ? WHERE USER_ID = ?")) {
                try (Statement s = connection.createStatement();
                     ResultSet rs = s.executeQuery("SELECT USER_ID FROM USERS WHERE USER_TYPE = 'LOCAL'")) {
                    while (rs.next()) {
                        UUID userId = (UUID) rs.getObject(1);
                        addIdentity(identity, UUID.randomUUID(), userId, "local", userId.toString());
                    }
                }
                for (Map.Entry<String, List<UUID>> entry : ldapSubjects.entrySet()) {
                    UUID userId = entry.getValue().get(0);
                    UUID identityId = UUID.randomUUID();
                    addIdentity(identity, identityId, userId, "ldap", entry.getKey());
                    adoption.setObject(1, userId);
                    adoption.setString(2, entry.getKey());
                    adoption.executeUpdate();
                    profile.setObject(1, identityId);
                    profile.setObject(2, userId);
                    profile.executeUpdate();
                }
            }

            // Keep cached LDAP groups even for legacy non-LDAP accounts; their unlinked
            // bindings remain ineffective until reconciled by the authorization service.
            try (Statement s = connection.createStatement();
                 ResultSet rs = s.executeQuery("SELECT u.USER_ID, i.IDENTITY_ID FROM USERS u LEFT JOIN USER_IDENTITIES i ON i.USER_ID = u.USER_ID AND i.PROVIDER_ID = 'ldap' AND i.IS_ACTIVE WHERE u.USER_TYPE = 'LDAP' OR EXISTS (SELECT 1 FROM USER_LDAP_GROUPS g WHERE g.USER_ID = u.USER_ID)" );
                 PreparedStatement binding = connection.prepareStatement("INSERT INTO USER_AUTHORIZATION_BINDINGS (BINDING_ID, USER_ID, SOURCE_ID, IDENTITY_ID, IS_REQUIRED, ORIGIN, REFRESH_VERSION) VALUES (?, ?, 'ldap', ?, false, 'DEFAULT', 0)")) {
                while (rs.next()) {
                    binding.setObject(1, UUID.randomUUID());
                    binding.setObject(2, rs.getObject(1));
                    binding.setObject(3, rs.getObject(2));
                    binding.executeUpdate();
                }
            }
            execute(connection, "INSERT INTO USER_AUTHORIZATION_STATE (USER_ID, SOURCE_ID, BINDING_ID, STATUS, OBSERVED_AT) SELECT b.USER_ID, b.SOURCE_ID, b.BINDING_ID, 'STALE', u.LAST_GROUP_SYNC_DT FROM USER_AUTHORIZATION_BINDINGS b JOIN USERS u ON u.USER_ID = b.USER_ID");
            execute(connection, "INSERT INTO USER_SOURCE_GROUPS (USER_ID, SOURCE_ID, GROUP_ID) SELECT DISTINCT USER_ID, 'ldap', LDAP_GROUP FROM USER_LDAP_GROUPS");
            execute(connection, "INSERT INTO ROLE_SOURCE_GROUPS (ROLE_ID, SOURCE_ID, GROUP_ID) SELECT DISTINCT ROLE_ID, 'ldap', LDAP_GROUP FROM ROLE_LDAP_GROUPS");
            execute(connection, "INSERT INTO TEAM_SOURCE_GROUPS (TEAM_ID, SOURCE_ID, GROUP_ID, TEAM_ROLE) SELECT DISTINCT TEAM_ID, 'ldap', LDAP_GROUP, TEAM_ROLE FROM TEAM_LDAP_GROUPS");
        } catch (CustomChangeException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomChangeException("Unable to migrate legacy security data", e);
        }
    }

    private static void addIdentity(PreparedStatement statement, UUID identityId, UUID userId, String provider, String subject) throws Exception {
        statement.setObject(1, identityId);
        statement.setObject(2, userId);
        statement.setString(3, provider);
        statement.setString(4, subject);
        statement.executeUpdate();
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Legacy security data migrated";
    }

    @Override
    public void setUp() throws SetupException {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}
