package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.xml.bind.DatatypeConverter;
import java.util.*;
import java.util.stream.Collectors;

public class ConcordTaskParams {

    public static ConcordTaskParams of(Variables input, Map<String, Object> defaults) {
        Map<String, Object> variablesMap = new HashMap<>(defaults != null ? defaults : Collections.emptyMap());
        variablesMap.putAll(input.toMap());

        Variables variables = new MapBackedVariables(variablesMap);

        ConcordTaskParams p = new ConcordTaskParams(variables);
        switch (p.action()) {
            case START: {
                return new StartParams(variables);
            }
            case STARTEXTERNAL: {
                return new StartExternalParams(variables);
            }
            case FORK: {
                return new ForkParams(variables);
            }
            case KILL: {
                return new KillParams(variables);
            }
            default: {
                throw new IllegalArgumentException("Unsupported action type: " + p.action());
            }
        }
    }

    protected final Variables variables;

    ConcordTaskParams(Variables variables) {
        this.variables = variables;
    }

    public Action action() {
        String action = variables.assertString(Keys.ACTION_KEY);
        try {
            return Action.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown action: '" + action + "'. Available actions: " + Arrays.toString(Action.values()));
        }
    }

    public static class ListSubProcesses {

        public static ListSubProcesses of(UUID instanceId, String... tags) {
            Map<String, Object> vars = new HashMap<>();
            vars.put(INSTANCE_ID_KEY, instanceId);
            if (tags != null) {
                vars.put(TAGS_KEY, Arrays.asList(tags));
            }
            return new ListSubProcesses(new MapBackedVariables(vars));
        }

        private static final String INSTANCE_ID_KEY = "instanceId";
        private static final String TAGS_KEY = "tags";

        private final Variables variables;

        public ListSubProcesses(Variables variables) {
            this.variables = variables;
        }

        public UUID instanceId() {
            return variables.assertUUID(INSTANCE_ID_KEY);
        }

        public Set<String> tags() {
            return getSet(variables, TAGS_KEY);
        }
    }

    public static class StartParams extends ConcordTaskParams {

        private static final String ACTIVE_PROFILES_KEY = "activeProfiles";
        protected static final String API_KEY = "apiKey";
        private static final String ARGUMENTS_KEY = "arguments";
        private static final String ATTACHMENTS_KEY = "attachments";
        private static final String BASE_URL_KEY = "baseUrl";
        private static final String DEBUG_KEY = "debug";
        private static final String DISABLE_ON_CANCEL_KEY = "disableOnCancel";
        private static final String DISABLE_ON_FAILURE_KEY = "disableOnFailure";
        private static final String EXCLUSIVE_KEY = "exclusive";
        private static final String IGNORE_FAILURES_KEY = "ignoreFailures";
        private static final String META_KEY = "meta";
        private static final String ORG_KEY = "org";
        private static final String OUT_VARS_KEY = "outVars";
        private static final String REPO_BRANCH_OR_TAG_KEY = "repoBranchOrTag";
        private static final String REPO_COMMIT_ID_KEY = "repoCommitId";
        private static final String REQUIREMENTS_KEY = "requirements";
        private static final String START_AT_KEY = "startAt";
        public static final String SUSPEND_KEY = "suspend";
        private static final String SYNC_KEY = "sync";
        private static final String TAGS_KEY = "tags";
        protected static final String ENTRY_POINT_KEY = "entryPoint";
        public static final String PAYLOAD_KEY = "payload";
        public static final String PROJECT_KEY = "project";
        public static final String REPO_KEY = "repo";

        StartParams(Variables variables) {
            super(variables);
        }

        public String payload() {
            return variables.getString(PAYLOAD_KEY);
        }

        public Set<String> activeProfiles() {
            return getSet(variables, ACTIVE_PROFILES_KEY);
        }

        public String entryPoint() {
            return variables.getString(ENTRY_POINT_KEY);
        }

        public Map<String, Object> exclusive() {
            return variables.getMap(EXCLUSIVE_KEY, Collections.emptyMap());
        }

        public Set<String> tags() {
            return getSet(variables, TAGS_KEY);
        }

        public Map<String, Object> arguments() {
            return variables.getMap(ARGUMENTS_KEY, Collections.emptyMap());
        }

        public boolean disableOnCancel() {
            return variables.getBoolean(DISABLE_ON_CANCEL_KEY, false);
        }

        public boolean disableOnFailure() {
            return variables.getBoolean(DISABLE_ON_FAILURE_KEY, false);
        }

        public Collection<String> outVars() {
            return variables.getCollection(OUT_VARS_KEY, Collections.emptyList());
        }

        public Object meta() {
            return variables.get(META_KEY);
        }

        public Map<String, Object> requirements() {
            return variables.getMap(REQUIREMENTS_KEY, Collections.emptyMap());
        }

        public String repo() {
            return variables.getString(REPO_KEY);
        }

        public Collection<Object> attachments() {
            return variables.getCollection(ATTACHMENTS_KEY, Collections.emptyList());
        }

        public String repoBranchOrTag() {
            return variables.getString(REPO_BRANCH_OR_TAG_KEY);
        }

        public String repoCommitId() {
            return variables.getString(REPO_COMMIT_ID_KEY);
        }

        public String startAt() {
            Object v = variables.get(START_AT_KEY);
            if (v == null) {
                return null;
            }

            if (v instanceof String) {
                return (String) v;
            } else if (v instanceof Date) {
                Calendar c = Calendar.getInstance();
                c.setTime((Date) v);
                return DatatypeConverter.printDateTime(c);
            } else if (v instanceof Calendar) {
                return DatatypeConverter.printDateTime((Calendar) v);
            } else {
                throw new IllegalArgumentException("'" + START_AT_KEY + "' must be a string, java.util.Date or java.util.Calendar value. Got: " + v);
            }
        }

        public boolean sync() {
            return variables.getBoolean(SYNC_KEY, false);
        }

        public boolean debug(boolean defaultValue) {
            return variables.getBoolean(DEBUG_KEY, defaultValue);
        }

        public String org() {
            return variables.getString(ORG_KEY);
        }

        public boolean suspend() {
            return variables.getBoolean(SUSPEND_KEY, false);
        }

        public boolean ignoreFailures() {
            return variables.getBoolean(IGNORE_FAILURES_KEY, false);
        }

        public String project() {
            return variables.getString(PROJECT_KEY);
        }

        public String baseUrl() {
            return variables.getString(BASE_URL_KEY);
        }

        public String apiKey() {
            return variables.getString(API_KEY);
        }
    }

    static class StartExternalParams extends StartParams {

        StartExternalParams(Variables variables) {
            super(variables);
        }

        @Override
        public String apiKey() {
            return variables.assertString("'" + API_KEY + "' is required to start a process on an external Concord instance", API_KEY);
        }

        @Override
        public boolean suspend() {
            return false;
        }

        public boolean suspendRaw() {
            return super.suspend();
        }
    }

    static class ForkParams extends ConcordTaskParams {

        private static final String IGNORE_FAILURES_KEY = "ignoreFailures";
        private static final String SYNC_KEY = "sync";
        private static final String FORKS_KEY = "forks";
        private static final String SUSPEND_KEY = "suspend";
        private static final String OUT_VARS_KEY = "outVars";

        ForkParams(Variables variables) {
            super(variables);
        }

        public Collection<String> outVars() {
            return variables.getCollection(OUT_VARS_KEY, Collections.emptyList());
        }

        public List<ForkStartParams> forks() {
            List<ForkStartParams> forks;
            Collection<Map<String, Object>> forksValue = variables.getCollection(FORKS_KEY, null);
            if (forksValue == null) {
                forks = Collections.singletonList(new ForkStartParams(variables));
            } else {
                forks = forksValue.stream()
                        .map(f -> {
                            // some parameters (e.g. tags) can be defined for either an "forks" entry or "globally"
                            // for example:
                            // - task: concord
                            //   in:
                            //     tags: ["red"]
                            //     forks:
                            //       - entryPoint: "x" // inherits tags value
                            //       - entryPoint: "y"
                            //         tags: ["green"] // provides its own tags
                            //
                            // that's why here we're using DelegateVariables which looks for keys in
                            // the "forks" entry first and then falls back to the "global" section.
                            Variables vars = new DelegateVariables(new MapBackedVariables(f), variables);
                            return new ForkStartParams(vars, outVars());
                        })
                        .collect(Collectors.toList());
            }

            if (forks.isEmpty()) {
                throw new IllegalArgumentException("'" + FORKS_KEY + "' can't be an empty list");
            }

            return forks;
        }

        public boolean sync() {
            return variables.getBoolean(SYNC_KEY, false);
        }

        public boolean ignoreFailures() {
            return variables.getBoolean(IGNORE_FAILURES_KEY, false);
        }

        public boolean suspend() {
            return variables.getBoolean(SUSPEND_KEY, false);
        }
    }

    static class ForkStartParams extends StartParams {

        private static final String INSTANCES_KEY = "instances";
        private final Collection<String> outVars;

        ForkStartParams(Variables variables) {
            this(variables, Collections.emptyList());
        }

        ForkStartParams(Variables variables, Collection<String> outVars) {
            super(variables);
            this.outVars = outVars;
        }

        @Override
        public Action action() {
            return Action.FORK;
        }

        @Override
        public String entryPoint() {
            String entryPoint = super.entryPoint();
            if (entryPoint != null) {
                return entryPoint;
            }

            throw new IllegalArgumentException("'" + ENTRY_POINT_KEY + "' is required");
        }

        @Override
        public Collection<String> outVars() {
            Collection<String> outVars = super.outVars();
            if (!outVars.isEmpty()) {
                return outVars;
            }
            return this.outVars;
        }

        public int getInstances() {
            Object v = variables.get(INSTANCES_KEY);
            if (v == null) {
                return 1;
            }

            int i;
            if (v instanceof Integer) {
                i = (Integer) v;
            } else if (v instanceof Long) {
                i = ((Long) v).intValue();
            } else {
                throw new IllegalArgumentException("'" + INSTANCES_KEY + "' must be a number");
            }

            if (i <= 0) {
                throw new IllegalArgumentException("'" + INSTANCES_KEY + "' must be a positive number");
            }

            return i;
        }
    }

    public static class KillParams extends ConcordTaskParams {

        private static final String INSTANCE_ID_KEY = "instanceId";
        private static final String SYNC_KEY = "sync";

        public KillParams(Variables variables) {
            super(variables);
        }

        @Override
        public Action action() {
            return Action.KILL;
        }

        @SuppressWarnings("rawtypes")
        public List<UUID> ids() {
            List<UUID> ids = new ArrayList<>();

            Object v = variables.get(INSTANCE_ID_KEY);
            if (v instanceof String || v instanceof UUID) {
                ids.add(toUUID(v));
            } else if (v instanceof String[] || v instanceof UUID[]) {
                Object[] os = (Object[]) v;
                for (Object o : os) {
                    ids.add(toUUID(o));
                }
            } else if (v instanceof Collection) {
                for (Object o : (Collection) v) {
                    if (o instanceof String || o instanceof UUID) {
                        ids.add(toUUID(o));
                    } else {
                        throw new IllegalArgumentException("'" + INSTANCE_ID_KEY + "' value should be a string or an UUID: " + o);
                    }
                }
            } else {
                throw new IllegalArgumentException("'" + INSTANCE_ID_KEY + "' should be a single string, an UUID value or an array of strings or UUIDs: " + v);
            }

            return ids;
        }

        public boolean sync() {
            return variables.getBoolean(SYNC_KEY, false);
        }
    }

    private static UUID toUUID(Object v) {
        if (v instanceof String) {
            return UUID.fromString(v.toString());
        } else if (v instanceof UUID) {
            return (UUID) v;
        }
        throw new IllegalArgumentException("Invalid value type: expected UUID or String, got: " + v.getClass());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set<String> getSet(Variables variables, String key) {
        Object v = variables.get(key);
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            return Arrays.stream(((String) v)
                    .split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
        } else if (v instanceof String[]) {
            return new HashSet<>(Arrays.asList((String[]) v));
        } else if (v instanceof Collection) {
            return new HashSet<>((Collection) v);
        } else {
            throw new IllegalArgumentException("'" + key + "' must a single string value or an array of strings: " + v);
        }
    }

    public enum Action {

        START,
        STARTEXTERNAL,
        FORK,
        KILL
    }

    private static class DelegateVariables implements Variables {

        private final Variables[] delegates;

        public DelegateVariables(Variables... delegates) {
            this.delegates = delegates;
        }

        @Override
        public Object get(String key) {
            for (Variables delegate : delegates) {
                if (delegate.has(key)) {
                    return delegate.get(key);
                }
            }
            return null;
        }

        @Override
        public void set(String key, Object value) {
            throw new IllegalStateException("Not supported");
        }

        @Override
        public boolean has(String key) {
            for (Variables delegate : delegates) {
                if (delegate.has(key)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Map<String, Object> toMap() {
            throw new IllegalStateException("Not supported");
        }
    }
}
