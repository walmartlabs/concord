package com.walmartlabs.concord.sdk;

/**
 * Project-scoped key-value storage.
 * For the processes without a project, a default shared entry will be used.
 */
public interface KvService {

    void remove(String instanceId, String key) throws ClientException;

    void putString(String instanceId, String key, String value) throws ClientException;

    String getString(String instanceId, String key) throws ClientException;

    void putLong(String instanceId, String key, Long value) throws ClientException;

    long incLong(String instanceId, String key) throws ClientException;

    Long getLong(String instanceId, String key) throws ClientException;
}
