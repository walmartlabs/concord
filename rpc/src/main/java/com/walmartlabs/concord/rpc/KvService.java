package com.walmartlabs.concord.rpc;

/**
 * Project-scoped key-value storage.
 * For the processes without a project, a default shared entry will be used.
 */
public interface KvService {

    void remove(String key) throws ClientException;

    void put(String key, String value) throws ClientException;

    String get(String key) throws ClientException;

    long inc(String key) throws ClientException;
}
