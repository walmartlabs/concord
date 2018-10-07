package com.walmartlabs.concord.policyengine;

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
import java.util.function.Function;

import static com.walmartlabs.concord.policyengine.CheckResult.Item;

public class QueueProcessPolicy {

    private final QueueProcessRule process;
    private final QueueProcessRule processPerOrg;
    private final QueueProcessRule processPerProject;

    public QueueProcessPolicy(QueueProcessRule process, QueueProcessRule processPerOrg, QueueProcessRule processPerProject) {
        this.process = process;
        this.processPerOrg = processPerOrg;
        this.processPerProject = processPerProject;
    }

    public CheckResult<ProcessRule, Integer> check(Function<Set<String>, QueueMetrics> c) {
        Set<String> statuses = collectStatuses();
        if (statuses.isEmpty()) {
            return new CheckResult<>();
        }

        QueueMetrics m = c.apply(statuses);

        List<Item<ProcessRule, Integer>> errors = new ArrayList<>();

        check(process, m.getProcess(), errors);
        check(processPerOrg, m.getPerOrg(), errors);
        check(processPerProject, m.getPerProject(), errors);

        return new CheckResult<>(Collections.emptyList(), errors);
    }

    private static void check(QueueProcessRule rule, Map<String, Integer> actual,
                              List<Item<ProcessRule, Integer>> errors) {

        if (rule == null || rule.getMax() == null) {
            return;
        }

        for (Map.Entry<String, Integer> e : actual.entrySet()) {
            String status = e.getKey();
            int count = e.getValue();

            Integer limit = rule.getMax().get(status);
            if (limit != null && limit <= count) {
                errors.add(new Item<>(new ProcessRule(rule.getMsg(), status, limit), count));
            }
        }
    }

    private Set<String> collectStatuses() {
        Set<String> statuses = new HashSet<>();
        if (process != null && process.getMax() != null) {
            statuses.addAll(process.getMax().keySet());
        }
        if (processPerOrg != null && processPerOrg.getMax() != null) {
            statuses.addAll(processPerOrg.getMax().keySet());
        }
        if (processPerProject != null && processPerProject.getMax() != null) {
            statuses.addAll(processPerProject.getMax().keySet());
        }

        return statuses;
    }

    public static class QueueMetrics {

        private final Map<String, Integer> process;
        private final Map<String, Integer> perOrg;
        private final Map<String, Integer> perProject;

        public QueueMetrics(Map<String, Integer> process, Map<String, Integer> perOrg, Map<String, Integer> perProject) {
            this.process = process;
            this.perOrg = perOrg;
            this.perProject = perProject;
        }

        public Map<String, Integer> getProcess() {
            return process;
        }

        public Map<String, Integer> getPerOrg() {
            return perOrg;
        }

        public Map<String, Integer> getPerProject() {
            return perProject;
        }

        @Override
        public String toString() {
            return "QueueMetrics{" +
                    "process=" + process +
                    ", perOrg=" + perOrg +
                    ", perProject=" + perProject +
                    '}';
        }
    }

    public static class ProcessRule {

        private final String msg;
        private final String status;
        private final int max;

        public ProcessRule(String msg, String status, int max) {
            this.msg = msg;
            this.status = status;
            this.max = max;
        }

        public String getMsg() {
            return msg;
        }

        public String getStatus() {
            return status;
        }

        public int getMax() {
            return max;
        }

        @Override
        public String toString() {
            return "ProcessRule{" +
                    "msg='" + msg + '\'' +
                    ", status='" + status + '\'' +
                    ", max=" + max +
                    '}';
        }
    }
}
