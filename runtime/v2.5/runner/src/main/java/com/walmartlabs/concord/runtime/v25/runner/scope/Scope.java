package com.walmartlabs.concord.runtime.v25.runner.scope;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v25.model.Values;
import com.walmartlabs.concord.runtime.v25.runner.plan.ExecutionPlan;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Scope {

    private final Scope parent;
    private final String flowName;
    private final Set<String> flowNames;
    private final boolean dryRun;
    private final boolean debug;
    private final ExecutionPlan plan;
    private final LinkedHashMap<String, Object> overlay = new LinkedHashMap<>();

    private Scope(Scope parent, String flowName, Set<String> flowNames, boolean dryRun, boolean debug,
                  ExecutionPlan plan) {
        this.parent = parent;
        this.flowName = flowName;
        this.flowNames = flowNames;
        this.dryRun = dryRun;
        this.debug = debug;
        this.plan = plan;
    }

    public static Scope root(Map<String, Object> values, Set<String> flowNames, String flowName,
                             boolean dryRun, boolean debug, ExecutionPlan plan) {
        var result = new Scope(null, flowName, Set.copyOf(flowNames), dryRun, debug, plan);
        result.commit(values);
        return result;
    }

    public Scope child(String childFlowName) {
        return new Scope(this, childFlowName, flowNames, dryRun, debug, plan);
    }
    public Scope fork() {
        var base = root(snapshot(), flowNames, flowName, dryRun, debug, plan);
        return base.child(flowName);
    }

    public Lookup lookup(String key) {
        if (overlay.containsKey(key)) {
            return new Lookup(true, overlay.get(key));
        }
        return parent != null ? parent.lookup(key) : new Lookup(false, null);
    }

    public Object get(String key) {
        return lookup(key).value();
    }

    public boolean contains(String key) {
        return lookup(key).present();
    }

    public void set(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("Variable name must not be null");
        }
        if (key.indexOf('.') >= 0) {
            setDotted(List.of(key.split("\\.")), value);
            return;
        }
        ensureWorkflowValue(value, key);
        overlay.put(key, Values.freeze(value));
    }

    public void set(List<String> path, Object value) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Variable path must not be empty");
        }
        if (path.stream().anyMatch(part -> part == null || part.isEmpty())) {
            throw new IllegalArgumentException("Variable path must not contain null or empty segments");
        }
        if (path.size() == 1) {
            ensureWorkflowValue(value, path.getFirst());
            overlay.put(path.getFirst(), Values.freeze(value));
            return;
        }
        setDotted(path, value);
    }

    public void commit(Map<String, Object> values) {
        var seen = new IdentityHashMap<Object, Boolean>();
        values.forEach((key, value) -> {
            if (key == null) {
                throw new IllegalArgumentException("Variable name must not be null");
            }
            ensureWorkflowValue(value, key, seen);
        });
        overlay.putAll(Values.map(values));
    }

    public Map<String, Object> snapshot() {
        var scopes = new ArrayList<Scope>();
        for (var current = this; current != null; current = current.parent) {
            scopes.add(current);
        }
        var result = new LinkedHashMap<String, Object>();
        for (var i = scopes.size() - 1; i >= 0; i--) {
            result.putAll(scopes.get(i).overlay);
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<String, Object> localValues() {
        return Collections.unmodifiableMap(overlay);
    }
    public Scope parent() {
        return parent;
    }


    public String flowName() {
        return flowName;
    }

    public boolean hasFlow(String name) {
        return flowNames.contains(name);
    }

    public boolean dryRun() {
        return dryRun;
    }

    public boolean debug() {
        return debug;
    }

    public ExecutionPlan plan() {
        if (plan == null) {
            throw new IllegalStateException("No process plan is associated with this scope");
        }
        return plan;
    }

    @SuppressWarnings("unchecked")
    private void setDotted(List<String> parts, Object value) {
        var rootLookup = lookup(parts.getFirst());
        if (rootLookup.present() && !(rootLookup.value() instanceof Map<?, ?>)) {
            var type = rootLookup.value() == null ? "null" : rootLookup.value().getClass().getName();
            throw new IllegalArgumentException("Cannot set nested variable '" + String.join(".", parts)
                    + "': variable '" + parts.getFirst() + "' has type " + type);
        }
        var root = rootLookup.value() instanceof Map<?, ?> existing
                ? new LinkedHashMap<String, Object>((Map<String, Object>) existing)
                : new LinkedHashMap<String, Object>();
        var current = root;
        for (var i = 1; i < parts.size() - 1; i++) {
            var nested = current.get(parts.get(i));
            var copy = nested instanceof Map<?, ?> existing
                    ? new LinkedHashMap<String, Object>((Map<String, Object>) existing)
                    : new LinkedHashMap<String, Object>();
            current.put(parts.get(i), copy);
            current = copy;
        }
        var key = String.join(".", parts);
        ensureWorkflowValue(value, key);
        current.put(parts.getLast(), Values.freeze(value));
        overlay.put(parts.getFirst(), Values.freeze(root));
    }

    private static void ensureWorkflowValue(Object value, String key) {
        ensureWorkflowValue(value, key, new IdentityHashMap<>());
    }

    private static void ensureWorkflowValue(Object value, String key, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) {
            return;
        }
        if (seen.put(value, Boolean.TRUE) != null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((mapKey, item) -> {
                if (mapKey == null) {
                    throw new IllegalArgumentException("Variable '" + key + "' contains a null map key");
                }
                ensureWorkflowValue(mapKey, key, seen);
                ensureWorkflowValue(item, key, seen);
            });
            return;
        }
        if (value instanceof Collection<?> collection) {
            if (!(value instanceof Serializable)) {
                throw new IllegalArgumentException("Variable '" + key + "' contains non-serializable value of type "
                        + value.getClass().getName());
            }
            collection.forEach(item -> ensureWorkflowValue(item, key, seen));
            return;
        }
        if (value instanceof Map.Entry<?, ?> entry) {
            ensureWorkflowValue(entry.getKey(), key, seen);
            ensureWorkflowValue(entry.getValue(), key, seen);
            return;
        }
        if (value.getClass().isArray()) {
            for (var i = 0; i < Array.getLength(value); i++) {
                ensureWorkflowValue(Array.get(value, i), key, seen);
            }
            return;
        }
        if (!(value instanceof Serializable)) {
            throw new IllegalArgumentException("Variable '" + key + "' contains non-serializable value of type "
                    + value.getClass().getName());
        }
    }

    public record Lookup(boolean present, Object value) {
    }
}
