package com.walmartlabs.concord.runtime.v2.sdk;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of a task call. Provides some common fields such as {@link #ok()}
 * and {@link #error()}, allows arbitrary data in {@link #values()}.
 * <p/>
 * All values must be {@link Serializable}, including collection types.
 * Avoid using custom types/classes as values.
 */
public class TaskResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance of {@link TaskResult} with {@link #ok()} set to {@code true}.
     */
    public static TaskResult success() {
        return new TaskResult(true, null, null);
    }

    /**
     * Creates a new instance of {@link TaskResult} with {@link #ok()} set to {@code false}
     * and with the provided error message.
     */
    public static TaskResult error(String message) {
        return new TaskResult(false, message, null);
    }

    public static TaskResult suspend(String eventName) {
        return new TaskResult(true, null, null, SuspendAction.SUSPEND, eventName, null);
    }

    public static TaskResult suspendResume(String eventName, Map<String, Serializable> suspendState) {
        return new TaskResult(true, null, null, SuspendAction.SUSPEND_RESUME, eventName, suspendState);
    }

    private final boolean ok;
    private final String error;
    private Map<String, Object> values;

    private final SuspendAction suspendAction;
    private final String eventName;
    private final Map<String, Serializable> suspendState;

    public TaskResult(boolean ok) {
        this(ok, null);
    }

    public TaskResult(boolean ok, String error) {
        this(ok, error, null);
    }

    public TaskResult(boolean ok, String error, Map<String, Object> values) {
        this(ok, error, values, null, null, null);
    }

    private TaskResult(boolean ok, String error, Map<String, Object> values, SuspendAction suspendAction, String eventName, Map<String, Serializable> suspendState) {
        this.ok = ok;
        this.error = error;

        // make sure the map is serializable and mutable
        this.values = values != null ? new HashMap<>(values) : null;

        this.suspendAction = suspendAction;
        this.eventName = eventName;
        this.suspendState = suspendState != null ? new HashMap<>(suspendState) : null;
    }

    public boolean ok() {
        return ok;
    }

    @Nullable
    public String error() {
        return error;
    }

    public SuspendAction suspendAction() {
        return suspendAction;
    }

    @Nullable
    public String suspendEvent() {
        return eventName;
    }

    public Map<String, Serializable> suspendState() {
        return suspendState;
    }

    public TaskResult value(String key, Object value) {
        if (values == null) {
            values = new HashMap<>();
        }

        assertValue(key, value);

        values.put(key, value);
        return this;
    }

    public TaskResult values(Map<String, Object> items) {
        if (items == null) {
            return this;
        }

        for (Map.Entry<String, Object> e : items.entrySet()) {
            value(e.getKey(), e.getValue());
        }

        return this;
    }

    public Map<String, Object> values() {
        if (values == null) {
            return Collections.emptyMap();
        }
        return values;
    }

    /**
     * Returns a combined map of all values plus additional fields:
     * <ul>
     *     <li>{@code ok} - a boolean value, same as {@link #ok()}</li>
     *     <li>{@code error} - a string value, same as {@link #error()}</li>
     * </ul>
     * <p>
     * Those fields will override any values with the same keys.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>(values != null ? values : Collections.emptyMap());
        result.put("ok", ok);
        if (error != null) {
            result.put("error", error);
        }
        return result;
    }

    private static void assertValue(String key, Object value) {
        if (value == null) {
            return;
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())) {
            oos.writeObject(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Can't set the '%s' key: " +
                    "not a serializable value: %s (class: %s). " +
                    "Error: %s", key, value, value.getClass(), e.getMessage()));
        }
    }

    public enum SuspendAction {
        SUSPEND, SUSPEND_RESUME
    }
}
