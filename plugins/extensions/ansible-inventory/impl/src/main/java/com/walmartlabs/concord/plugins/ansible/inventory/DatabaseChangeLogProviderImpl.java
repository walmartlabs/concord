package com.walmartlabs.concord.plugins.ansible.inventory;

import com.walmartlabs.concord.bootstrap.db.DatabaseChangeLogProvider;

import javax.inject.Named;

@Named
public class DatabaseChangeLogProviderImpl implements DatabaseChangeLogProvider {

    @Override
    public String getChangeLogPath() {
        // TODO pull from pom.xml
        return "com/walmartlabs/concord/plugins/ansible/inventory/db/liquibase.xml";
    }

    @Override
    public String getChangeLogTable() {
        return "ANSIBLE_INV_DB_LOG";
    }

    @Override
    public String getLockTable() {
        return "ANSIBLE_INV_DB_LOCK";
    }

    @Override
    public String toString() {
        return "ansible-inventory-extension";
    }
}
