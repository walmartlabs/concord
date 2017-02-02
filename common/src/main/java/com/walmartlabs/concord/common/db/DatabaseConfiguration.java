package com.walmartlabs.concord.common.db;

import org.jooq.SQLDialect;

import java.io.Serializable;

public class DatabaseConfiguration implements Serializable {

    private final SQLDialect dialect;
    private final String driverClassName;
    private final String url;
    private final String username;
    private final String password;

    public DatabaseConfiguration(SQLDialect dialect, String driverClassName, String url, String username, String password) {
        this.dialect = dialect;
        this.driverClassName = driverClassName;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public SQLDialect getDialect() {
        return dialect;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
