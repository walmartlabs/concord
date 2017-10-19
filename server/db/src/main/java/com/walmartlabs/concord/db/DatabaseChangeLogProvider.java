package com.walmartlabs.concord.db;

public interface DatabaseChangeLogProvider {

    String getChangeLogPath();

    String getChangeLogTable();

    String getLockTable();
}
