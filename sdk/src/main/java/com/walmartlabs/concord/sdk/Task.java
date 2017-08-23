package com.walmartlabs.concord.sdk;

/**
 * Marker interface for Concord tasks.
 */
public interface Task {

    default void execute(Context ctx) throws Exception {
        throw new Exception("Not implemented");
    }
}
