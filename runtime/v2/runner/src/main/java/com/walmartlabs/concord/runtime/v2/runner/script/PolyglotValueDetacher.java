package com.walmartlabs.concord.runtime.v2.runner.script;

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

import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Replaces GraalJS guest objects (polyglot proxies backed by a JS context) with plain
 * Java copies. Guest objects become unusable once their polyglot context is closed,
 * so anything a script left behind in host objects must be detached before closing.
 * <p>
 * Host containers are updated in place where possible to keep shared references
 * (e.g. the same map referenced from multiple variables or frames) consistent.
 */
public final class PolyglotValueDetacher {

    private static final Logger log = LoggerFactory.getLogger(PolyglotValueDetacher.class);

    private static final String POLYGLOT_PACKAGE = "com.oracle.truffle.polyglot.";

    /**
     * Detaches guest objects reachable from {@code value}.
     *
     * @return {@code value} itself if it was left unchanged or updated in place,
     * otherwise a detached copy that must replace the original reference.
     */
    public static Object detach(Object value) {
        return detach(value, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /**
     * Detaches guest objects in each map value, replacing entries in place.
     */
    public static void detachValues(Map<String, Object> values) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object v = e.getValue();
            Object detached = detach(v, visited);
            if (detached != v) {
                e.setValue(detached);
            }
        }
    }

    static boolean isGuest(Object o) {
        return o instanceof Value || (o != null && o.getClass().getName().startsWith(POLYGLOT_PACKAGE));
    }

    private static Object detach(Object value, Set<Object> visited) {
        if (value == null) {
            return null;
        }

        if (isGuest(value)) {
            return toHost(value, visited);
        }

        if (!visited.add(value)) {
            return value;
        }

        if (value instanceof Map<?, ?> m) {
            return detachMap(m, visited);
        } else if (value instanceof List<?> l) {
            return detachList(l, visited);
        } else if (value instanceof Collection<?> c) {
            return detachCollection(c, visited);
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    private static Object detachMap(Map<?, ?> m, Set<Object> visited) {
        Map<Object, Object> replacements = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            Object v = e.getValue();
            Object detached = detach(v, visited);
            if (detached != v) {
                replacements.put(e.getKey(), detached);
            }
        }

        if (replacements.isEmpty()) {
            return m;
        }

        try {
            ((Map<Object, Object>) m).putAll(replacements);
            return m;
        } catch (UnsupportedOperationException e) {
            Map<Object, Object> copy = new LinkedHashMap<>(m);
            copy.putAll(replacements);
            return copy;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object detachList(List<?> l, Set<Object> visited) {
        List<Object> result = null;
        boolean copied = false;

        for (int i = 0; i < l.size(); i++) {
            Object v = l.get(i);
            Object detached = detach(v, visited);
            if (detached == v) {
                continue;
            }

            if (result == null) {
                result = (List<Object>) l;
            }

            if (!copied) {
                try {
                    result.set(i, detached);
                    continue;
                } catch (UnsupportedOperationException e) {
                    result = new ArrayList<>(l);
                    copied = true;
                }
            }
            result.set(i, detached);
        }

        return result == null ? l : result;
    }

    @SuppressWarnings("unchecked")
    private static Object detachCollection(Collection<?> c, Set<Object> visited) {
        boolean changed = false;
        List<Object> items = new ArrayList<>(c.size());
        for (Object v : c) {
            Object detached = detach(v, visited);
            changed |= detached != v;
            items.add(detached);
        }

        if (!changed) {
            return c;
        }

        try {
            Collection<Object> target = (Collection<Object>) c;
            target.clear();
            target.addAll(items);
            return c;
        } catch (UnsupportedOperationException e) {
            return c instanceof Set ? new LinkedHashSet<>(items) : items;
        }
    }

    private static Object toHost(Object guest, Set<Object> visited) {
        if (guest instanceof Value v) {
            if (v.isHostObject()) {
                return detach(v.asHostObject(), visited);
            }
            if (v.isNull()) {
                return null;
            }
            if (v.isString()) {
                return v.asString();
            }
            if (v.isBoolean()) {
                return v.asBoolean();
            }
            if (v.isNumber()) {
                return v.as(Number.class);
            }
            if (v.hasArrayElements() || v.hasMembers() || v.hasHashEntries()) {
                return toHost(v.as(Object.class), visited);
            }
            return unsupported(guest);
        }

        if (guest instanceof Map<?, ?> m) {
            Map<Object, Object> result = new LinkedHashMap<>();
            m.forEach((k, v) -> result.put(detach(k, visited), detach(v, visited)));
            return result;
        } else if (guest instanceof List<?> l) {
            List<Object> result = new ArrayList<>(l.size());
            l.forEach(v -> result.add(detach(v, visited)));
            return result;
        } else if (guest instanceof Map.Entry<?, ?> e) {
            return new AbstractMap.SimpleEntry<>(detach(e.getKey(), visited), detach(e.getValue(), visited));
        }

        return unsupported(guest);
    }

    private static Object unsupported(Object guest) {
        // e.g. JS functions: they can't outlive the script's context
        log.warn("Script value of type '{}' cannot be used outside of the script, replacing with null", guest.getClass().getSimpleName());
        return null;
    }

    private PolyglotValueDetacher() {
    }
}
