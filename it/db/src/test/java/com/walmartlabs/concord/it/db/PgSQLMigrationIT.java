package com.walmartlabs.concord.it.db;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;

public class PgSQLMigrationIT {

    private Connection connection;

    @Before
    public void setUp() throws Exception {
        int port = Integer.parseInt(System.getenv("IT_DB_PORT"));

        Class.forName("org.postgresql.Driver");
        connection = DriverManager.getConnection("jdbc:postgresql://localhost:" + port + "/postgres", "postgres", "it");
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    public void test() throws Exception {
        String[] logs = {
                "com/walmartlabs/concord/server/db/liquibase.xml"
        };

        Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        for (String l : logs) {
            Liquibase lb = new Liquibase(l, new ClassLoaderResourceAccessor(), db);
            lb.update((String) null);
        }
    }
}
