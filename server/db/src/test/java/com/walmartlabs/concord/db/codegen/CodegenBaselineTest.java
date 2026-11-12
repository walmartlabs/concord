package com.walmartlabs.concord.db.codegen;

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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CodegenBaselineTest {

    private static String dbImage;

    @BeforeAll
    public static void setUp() throws Exception {
        var props = new Properties();
        try (var in = CodegenBaselineTest.class.getClassLoader().getResourceAsStream("db.properties")) {
            props.load(in);
        }
        dbImage = props.getProperty("db.image");
    }

    @SuppressWarnings("resource")
    private PostgreSQLContainer<?> createDbContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse(dbImage)
                .asCompatibleSubstituteFor("postgres"))
                .waitingFor(Wait.forListeningPort());
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void rejectsNonemptyDatabaseWithoutChangingIt() throws Exception {
        try (var db = createDbContainer()) {
            db.start();
            try (var connection = openConnection(db)) {
                var serverVersion = serverVersion(connection);
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE public.codegen_baseline_sentinel (id integer PRIMARY KEY, value text NOT NULL)");
                    statement.executeUpdate("INSERT INTO public.codegen_baseline_sentinel (id, value) VALUES (1, 'unchanged')");
                }

                var candidate = "CREATE TABLE public.codegen_baseline_candidate (id integer PRIMARY KEY);\n";
                assertThrows(SQLException.class,
                        () -> CodegenBaseline.restoreSchema(connection, candidate, serverVersion));

                assertTrue(connection.getAutoCommit());
                assertEquals(1, queryInt(connection,
                        "SELECT count(*) FROM public.codegen_baseline_sentinel WHERE id = 1 AND value = 'unchanged'"));
                assertEquals(0, queryInt(connection,
                        "SELECT count(*) FROM pg_catalog.pg_class c "
                                + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                                + "WHERE n.nspname = 'public' AND c.relname = 'codegen_baseline_candidate'"));
            }
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void restoresWholeScriptTransactionally() throws Exception {
        try (var db = createDbContainer()) {
            db.start();
            try (var connection = openConnection(db)) {
                var serverVersion = serverVersion(connection);
                var validScript = """
                        CREATE TYPE public.codegen_baseline_mood AS ENUM ('happy', 'sad');

                        CREATE TABLE public.codegen_baseline_message (
                            id integer PRIMARY KEY,
                            mood public.codegen_baseline_mood NOT NULL,
                            body text NOT NULL
                        );

                        CREATE OR REPLACE FUNCTION public.codegen_baseline_describe(p_body text)
                        RETURNS text
                        LANGUAGE plpgsql
                        AS $function$
                        DECLARE
                            result text;
                        BEGIN
                            result := p_body || E'\\\\;quoted';
                            IF p_body = 'needle' THEN
                                result := result || ';internal';
                            END IF;
                            RETURN result;
                        END;
                        $function$;
                        """;

                var failingScript = validScript + "\nSELECT 1 / 0;\n";
                assertThrows(SQLException.class,
                        () -> CodegenBaseline.restoreSchema(connection, failingScript, serverVersion));

                assertTrue(connection.getAutoCommit());
                assertEquals(0, queryInt(connection,
                        "SELECT count(*) FROM pg_catalog.pg_type t "
                                + "JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace "
                                + "WHERE n.nspname = 'public' AND t.typname = 'codegen_baseline_mood'"));
                assertEquals(0, queryInt(connection,
                        "SELECT count(*) FROM pg_catalog.pg_class c "
                                + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                                + "WHERE n.nspname = 'public' AND c.relname = 'codegen_baseline_message'"));
                assertEquals(0, queryInt(connection,
                        "SELECT count(*) FROM pg_catalog.pg_proc p "
                                + "JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace "
                                + "WHERE n.nspname = 'public' AND p.proname = 'codegen_baseline_describe'"));

                CodegenBaseline.restoreSchema(connection, validScript, serverVersion);
                assertTrue(connection.getAutoCommit());
                try (var insert = connection.prepareStatement(
                        "INSERT INTO public.codegen_baseline_message (id, mood, body) VALUES (?, ?::public.codegen_baseline_mood, ?)")) {
                    insert.setInt(1, 1);
                    insert.setString(2, "happy");
                    insert.setString(3, "needle");
                    assertEquals(1, insert.executeUpdate());
                }

                try (var function = connection.prepareStatement(
                        "SELECT public.codegen_baseline_describe(?)")) {
                    function.setString(1, "needle");
                    try (var result = function.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals("needle\\;quoted;internal", result.getString(1));
                        assertFalse(result.next());
                    }
                }
                assertEquals(1, queryInt(connection,
                        "SELECT count(*) FROM public.codegen_baseline_message WHERE id = 1 AND mood = 'happy'"));
            }
        }
    }

    private static Connection openConnection(PostgreSQLContainer<?> db) throws SQLException {
        return DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
    }

    private static int serverVersion(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SHOW server_version_num")) {
            assertTrue(result.next());
            return Integer.parseInt(result.getString(1));
        }
    }

    private static int queryInt(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            var value = result.getInt(1);
            assertFalse(result.next());
            return value;
        }
    }
}
