package com.walmartlabs.concord.common;

import com.walmartlabs.concord.sdk.Task;

public interface DynamicTaskRegistry {

    Task getByKey(String key);

    void register(Class<? extends Task> c);
}
