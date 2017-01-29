package com.walmartlabs.concord.db;

import com.walmartlabs.concord.bootstrap.db.DatabaseChangeLogProvider;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class DatabaseChangeLogProviderImpl implements DatabaseChangeLogProvider {

    @Override
    public String getChangeLogPath() {
        // TODO pull from pom.xml
        return "com/walmartlabs/concord/server/db/liquibase.xml";
    }

    @Override
    public String getChangeLogTable() {
        return "SERVER_DB_LOG";
    }

    @Override
    public String getLockTable() {
        return "SERVER_DB_LOCK";
    }

    @Override
    public String toString() {
        return "concord-db";
    }
}
