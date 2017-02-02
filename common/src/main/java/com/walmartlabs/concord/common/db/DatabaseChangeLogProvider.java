package com.walmartlabs.concord.common.db;

public interface DatabaseChangeLogProvider {

    String getChangeLogPath();

    String getChangeLogTable();

    String getLockTable();
}
