package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import java.util.*;

public final class CycleChecker {

    public static class CheckResult {
        private final boolean hasCycle;
        private final String node1;
        private final String node2;

        private CheckResult(boolean hasCycle, String node1, String node2) {
            this.hasCycle = hasCycle;
            this.node1 = node1;
            this.node2 = node2;
        }

        public static CheckResult noCycle() {
            return new CheckResult(false, null, null);
        }

        public static CheckResult cycle(String node1, String node2) {
            return new CheckResult(true, node1, node2);
        }

        public boolean isHasCycle() {
            return hasCycle;
        }

        public String getNode1() {
            return node1;
        }

        public String getNode2() {
            return node2;
        }

        @Override
        public String toString() {
            return hasCycle ? getNode1() + " <-> " + getNode2() : "no cycle";
        }
    }

    public static CheckResult check(Map<String, Object> m) {
        return check("root", m);
    }

    public static CheckResult check(String rootName, Map<String, Object> m) {
        Deque<N> visited = new ArrayDeque<>();
        return hasCycle(new N(rootName, m), visited);
    }

    private static CheckResult hasCycle(N node, Deque<N> visited) {
        if (node.getObject() == null) {
            return CheckResult.noCycle();
        }

        N n = find(visited, node);
        if (n != null) {
            return CheckResult.cycle(node.path, n.path);
        }

        visited.push(node);
        for (N nextNode : getNeighbours(node)) {
            CheckResult result = hasCycle(nextNode, visited);
            if (result.hasCycle) {
                return result;
            }
        }
        visited.pop();
        return CheckResult.noCycle();
    }

    private static N find(Collection<N> s, N n) {
        for (N v : s) {
            if (v.equals(n)) {
                return v;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<N> getNeighbours(N n) {
        Object element = n.getObject();

        if (element instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) element;

            List<N> result = new ArrayList<>(m.size());
            m.forEach((key, value) -> result.add(new N(n.getPath() + "." + key, value)));
            return result;
        } else if (element instanceof Collection) {
            Collection<Object> c = (Collection<Object>) element;

            List<N> result = new ArrayList<>(c.size());
            c.forEach(v -> result.add(new N(n.getPath(), v)));
            return result;
        }
        return Collections.emptyList();
    }

    private static class N {
        private final String path;
        private final Object object;

        public N(String path, Object object) {
            this.path = path;
            this.object = object;
        }

        public String getPath() {
            return path;
        }

        public Object getObject() {
            return object;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            N n = (N) o;
            return object == n.object;
        }

        @Override
        public int hashCode() {
            return Objects.hash(object);
        }

        @Override
        public String toString() {
            return getPath();
        }
    }

    private CycleChecker() {
    }
}
