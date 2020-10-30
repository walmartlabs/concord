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

import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public interface TaskResult extends Serializable {

    long serialVersionUID = 1L;

    /**
     * Creates a new instance of {@link TaskResult} with {@link SimpleResult#ok()} set to {@code true}.
     */
    static SimpleResult success() {
        return new SimpleResult(true, null, null);
    }

    static SimpleResult of(boolean success) {
        return new SimpleResult(success, null, null);
    }

    static SimpleResult of(boolean success, String error) {
        return new SimpleResult(success, error, null);
    }

    static SimpleResult of(boolean success, String error, Map<String, Object> values) {
        return new SimpleResult(success, error, values);
    }

    /**
     * Creates a new instance of {@link TaskResult} with {@link SimpleResult#ok()} set to {@code false}
     * and with the provided error message.
     */
    static SimpleResult error(String message) {
        return new SimpleResult(false, message, null);
    }

    static TaskResult suspend(String eventName) {
        return new SuspendResult(eventName);
    }

    static TaskResult reentrantSuspend(String eventName, Map<String, Serializable> payload) {
        return new ReentrantSuspendResult(eventName, payload);
    }

    void handle(Context ctx);

    /**
     * Result of a task call. Provides some common fields such as {@link #ok()}
     * and {@link #error()}, allows arbitrary data in {@link #values()}.
     * <p/>
     * All values must be {@link Serializable}, including collection types.
     * Avoid using custom types/classes as values.
     */
    class SimpleResult implements TaskResult {

        private final boolean ok;
        private final String error;
        private final Map<String, Object> values;

        SimpleResult(boolean ok, String error, Map<String, Object> values) {
            this.ok = ok;
            this.error = error;
            this.values = new HashMap<>();

            values(values);
        }

        public boolean ok() {
            return ok;
        }

        @Nullable
        public String error() {
            return error;
        }

        public Map<String, Object> values() {
            return values;
        }

        public SimpleResult value(String key, Object value) {
            assertValue(key, value);

            values.put(key, value);
            return this;
        }

        public SimpleResult values(Map<String, Object> items) {
            if (items == null) {
                return this;
            }

            for (Map.Entry<String, Object> e : items.entrySet()) {
                value(e.getKey(), e.getValue());
            }

            return this;
        }

        @Override
        public void handle(Context ctx) {
            Step step = ctx.execution().currentStep();
            if (!(step instanceof TaskCall)) {
                throw new IllegalStateException("Unexpected current step: " + step + ". This is most likely a bug.");
            }

            TaskCall taskCall = (TaskCall) step;
            String out = Objects.requireNonNull(taskCall.getOptions()).out();
            if (out != null) {
                ctx.variables().set(out, toMap());
            }
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
    }

    class SuspendResult implements TaskResult {

        private final String eventName;

        SuspendResult(String eventName) {
            this.eventName = eventName;
        }

        public String eventName() {
            return eventName;
        }

        @Override
        public void handle(Context ctx) {
            ctx.suspend(eventName);
        }
    }

    class ReentrantSuspendResult implements TaskResult {

        private final String eventName;
        private final Map<String, Serializable> payload;

        ReentrantSuspendResult(String eventName, Map<String, Serializable> payload) {
            this.eventName = eventName;
            this.payload = payload;
        }

        public String eventName() {
            return eventName;
        }

        public Map<String, Serializable> payload() {
            return payload;
        }

        @Override
        public void handle(Context ctx) {
            ctx.reentrantSuspend(eventName, payload);
        }
    }

    static void assertValue(String key, Object value) {
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
}
