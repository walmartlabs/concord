package com.walmartlabs.concord.bootstrap.db;

public interface DatabaseChangeLogProvider {

    String getChangeLogPath();

    String getChangeLogTable();

    String getLockTable();
}
