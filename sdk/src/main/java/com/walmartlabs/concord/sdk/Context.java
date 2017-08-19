package com.walmartlabs.concord.sdk;

import java.util.Set;

public interface Context {

    Object getVariable(String key);

    void setVariable(String key, Object value);

    void removeVariable(String key);

    Set<String> getVariableNames();

    <T> T eval(String expr, Class<T> type);

    Object interpolate(Object v);
}
