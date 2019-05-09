package com.walmartlabs.concord.agent.executors.runner;

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

import com.walmartlabs.concord.agent.logging.ProcessLog;
import com.walmartlabs.concord.common.DockerProcessBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DockerCommandBuilder {

    private static final String VOLUME_WORKSPACE_DEST = "/workspace";
    private static final String VOLUME_JAVA_DEST = "/opt/concord/java";
    private static final String VOLUME_RUNNER_DEST = "/opt/concord/runner";
    private static final String VOLUME_DEPS_DEST = "/opt/concord/deps";

    private final ProcessLog log;
    private final Path javaPath;

    private Path procDir;
    private UUID instanceId;
    private Path dependencyListsDir;
    private Path dependencyCacheDir;
    private Path runnerPath;
    private List<String> args = Collections.emptyList();
    private Collection<String> extraVolumes = Collections.emptyList();

    private final Cfg cfg;
    private final Map<String, Object> env = new HashMap<>();

    public DockerCommandBuilder(ProcessLog log, Path javaPath, Map<String, Object> cfg) {
        this.log = log;
        this.javaPath = javaPath;
        this.cfg = new Cfg(cfg);
    }

    public static String getJavaCmd() {
        return VOLUME_JAVA_DEST + "/bin/java";
    }

    public static Path getDependencyListsDir() {
        return Paths.get(VOLUME_DEPS_DEST);
    }

    public static Path getRunnerPath(Path baseRunnerPath) {
        return Paths.get(VOLUME_RUNNER_DEST).resolve(baseRunnerPath.getFileName());
    }

    public static Path getWorkspaceDir() {
        return Paths.get(VOLUME_WORKSPACE_DEST);
    }

    public DockerCommandBuilder procDir(Path procDir) {
        this.procDir = procDir;
        return this;
    }

    public DockerCommandBuilder instanceId(UUID instanceId) {
        this.instanceId = instanceId;
        return this;
    }

    public DockerCommandBuilder dependencyListsDir(Path dependencyListsDir) {
        this.dependencyListsDir = dependencyListsDir;
        return this;
    }

    public DockerCommandBuilder runnerPath(Path runnerPath) {
        this.runnerPath = runnerPath;
        return this;
    }

    public DockerCommandBuilder dependencyCacheDir(Path dependencyCacheDir) {
        this.dependencyCacheDir = dependencyCacheDir;
        return this;
    }

    public DockerCommandBuilder args(String[] args) {
        this.args = Arrays.asList(args);
        return this;
    }

    public DockerCommandBuilder extraEnv(String k, Object v) {
        this.env.put(k, v);
        return this;
    }

    public DockerCommandBuilder extraVolumes(Collection<String> extraVolumes) {
        this.extraVolumes = extraVolumes;
        return this;
    }

    public String[] build() throws IOException {
        boolean debug = cfg.getBoolean("debug", false);

        DockerProcessBuilder b = new DockerProcessBuilder(cfg.getString("image"))
                .addLabel(DockerProcessBuilder.CONCORD_TX_ID_LABEL, instanceId.toString())
                .cleanup(true)
                .useHostNetwork(true)
                .cpu(cfg.getString("cpu"))
                .memory(cfg.getString("ram"))
                .volume(procDir.toString(), VOLUME_WORKSPACE_DEST)
                .volume(javaPath.toString(), VOLUME_JAVA_DEST, true)
                .volume(runnerPath.getParent().toString(), VOLUME_RUNNER_DEST, true)
                .volume(dependencyListsDir.toString(), VOLUME_DEPS_DEST, true)
                .volume(dependencyCacheDir.toString(), dependencyCacheDir.toString(), true)
                .volume(procDir.toString(), procDir.toString())
                .env(stringify(combine(env, cfg.getMap("env"))))
                .forcePull(true)
                .options(cfg.getList("options"))
                .debug(debug)
                .args(this.args);

        if (extraVolumes != null) {
            extraVolumes.forEach(b::volume);
        }

        String[] cmd = b.buildCmd();

        if (debug) {
            log.info("CMD: {}", (Object) cmd);
        }

        return cmd;
    }

    private static Map<String, Object> combine(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> m = new HashMap<>(a);
        m.putAll(b);
        return m;
    }

    private static Map<String, String> stringify(Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }

            result.put(e.getKey(), v.toString());
        }

        return result;
    }

    private static class Cfg {

        private final Map<String, Object> cfg;

        private Cfg(Map<String, Object> cfg) {
            this.cfg = cfg;
        }

        @SuppressWarnings("unchecked")
        public <E> List<E> getList(String name) {
            Object v = cfg.get(name);
            if (v == null) {
                return Collections.emptyList();
            }

            if (v instanceof List) {
                return (List<E>) v;
            }
            throw new IllegalArgumentException("Expected a list value '" + name + "', got: " + v);
        }

        @SuppressWarnings("unchecked")
        public <K, V> Map<K, V> getMap(String name) {
            Object v = cfg.get(name);
            if (v == null) {
                return Collections.emptyMap();
            }

            if (v instanceof Map) {
                return (Map<K, V>) v;
            }
            throw new IllegalArgumentException("Expected a map value '" + name + "', got: " + v);
        }

        public String getString(String name) {
            Object v = cfg.get(name);
            if (v == null) {
                return null;
            }

            if (v instanceof String) {
                return (String) v;
            }
            return v.toString();
        }

        public boolean getBoolean(String name, boolean defaultValue) {
            Object v = cfg.get(name);
            if (v == null) {
                return defaultValue;
            }

            if (v instanceof Boolean) {
                return (Boolean) v;
            }
            throw new IllegalArgumentException("Expected a boolean value '" + name + "', got: " + v);
        }
    }
}
