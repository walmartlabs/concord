package com.walmartlabs.concord.common;

public interface DynamicTaskRegistry {

    Task getByKey(String key);

    void register(Class<? extends Task> c);
}
