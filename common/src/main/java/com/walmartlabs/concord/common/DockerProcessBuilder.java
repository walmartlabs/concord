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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DockerProcessBuilder {

    private static final Logger log = LoggerFactory.getLogger(DockerProcessBuilder.class);

    public static final String CONCORD_DOCKER_LOCAL_MODE_KEY = "CONCORD_DOCKER_LOCAL_MODE";
    public static final String CONCORD_TX_ID_LABEL = "concordTxId";
    private static final String DEFAULT_USER = "456"; // as in dockerPasswd

    private final String image;

    private String name;
    private String user = DEFAULT_USER;
    private String workdir;
    private String entryPoint;
    private String cpu;
    private String memory;

    private List<String> args = new ArrayList<>();
    private Map<String, String> env;
    private Map<String, String> labels;
    private List<Map.Entry<String, String>> volumes = new ArrayList<>();
    private List<Map.Entry<String, String>> options = new ArrayList<>();

    private boolean cleanup = true;
    private boolean debug = false;
    private boolean forcePull = true;
    private boolean generateUsers = false;
    private boolean exposeHostUsers = false;
    private boolean useHostUser = false;
    private boolean useHostNetwork = true;

    private boolean redirectErrorStream = true;

    public DockerProcessBuilder(String image) {
        this.image = image;

        if (Boolean.parseBoolean(env(CONCORD_DOCKER_LOCAL_MODE_KEY, "true"))) {
            // in the "local docker mode" we run all Docker processes using the current OS user's UID/GID
            // in order to do that, we need to mount the local /etc/passwd inside of the container
            log.warn("Running in the local Docker mode. Consider setting {}=false in the production environment.", CONCORD_DOCKER_LOCAL_MODE_KEY);

            this.exposeHostUsers = true;
            this.useHostUser = true;
        } else {
            this.generateUsers = true;
        }
    }

    public Process build() throws IOException {
        String[] cmd = buildCmd();

        if (debug) {
            log.info("CMD: {}", (Object) cmd);
        }

        return PrivilegedAction.perform("docker", () -> new ProcessBuilder(cmd)
                .redirectErrorStream(redirectErrorStream)
                .start());
    }

    public String[] buildCmd() throws IOException {
        if (forcePull) {
            return new String[]{"/bin/sh", "-c", "docker pull " + q(image) + " && " + buildDockerCmd()};
        } else {
            return new String[]{"/bin/sh", "-c", buildDockerCmd()};
        }
    }

    private String buildDockerCmd() throws IOException {
        List<String> c = new ArrayList<>();
        c.add("docker");
        c.add("run");
        if (name != null) {
            c.add("--name");
            c.add(q(name));
        }
        if (user != null && !useHostUser) {
            c.add("-u");
            c.add(user);
        }
        if (cleanup) {
            c.add("--rm");
        }
        c.add("-i");
        if (volumes != null) {
            volumes.forEach(v -> {
                c.add("-v");
                c.add(q(v.getKey() + ":" + v.getValue()));
            });
        }
        if (env != null) {
            env.forEach((k, v) -> {
                c.add("-e");
                c.add(q(k + "=" + v));
            });
        }
        if (workdir != null) {
            c.add("-w");
            c.add(q(workdir));
        }
        if (labels != null) {
            for (Map.Entry<String, String> l : labels.entrySet()) {
                String k = l.getKey();
                String v = l.getValue();

                c.add("--label");
                c.add(q(k + (v != null ? "=" + v : "")));
            }
        }
        if (entryPoint != null) {
            c.add("--entrypoint");
            c.add(entryPoint);
        }
        if (generateUsers) {
            Path tmp = IOUtils.createTempFile("passwd", ".docker");
            try (InputStream src = DockerProcessBuilder.class.getResourceAsStream("dockerPasswd");
                 OutputStream dst = Files.newOutputStream(tmp)) {
                IOUtils.copy(src, dst);
            }
            c.add("-v");
            c.add(tmp.toAbsolutePath().toString() + ":/etc/passwd:ro");
        }
        if (exposeHostUsers) {
            c.add("-v");
            c.add("/etc/passwd:/etc/passwd:ro");
        }
        if (useHostUser) {
            c.add("-u");
            c.add("`id -u`:`id -g`");

            c.add("-e");
            c.add("HOME=/tmp");
        }
        if (useHostNetwork) {
            c.add("--net=host");
        }
        if (cpu != null) {
            c.add("--cpus");
            c.add(cpu);
        }
        if (memory != null) {
            c.add("-m");
            c.add(memory);
        }
        options.forEach(o -> {
            c.add(o.getKey());
            if (o.getValue() != null) {
                c.add(o.getValue());
            }
        });
        c.add(q(image));
        if (args != null) {
            args.forEach(a -> c.add(q(a)));
        }
        return String.join(" ", c);
    }

    public DockerProcessBuilder cpu(String cpu) {
        this.cpu = cpu;
        return this;
    }

    public DockerProcessBuilder memory(String memory) {
        this.memory = memory;
        return this;
    }

    public DockerProcessBuilder name(String name) {
        this.name = name;
        return this;
    }

    public DockerProcessBuilder user(String user) {
        this.user = user;
        return this;
    }

    public DockerProcessBuilder addLabel(String k, String v) {
        if (labels == null) {
            labels = new HashMap<>();
        }
        labels.put(k, v);
        return this;
    }

    public DockerProcessBuilder debug(boolean debug) {
        this.debug = debug;
        return this;
    }

    public DockerProcessBuilder workdir(String workdir) {
        this.workdir = workdir;
        return this;
    }

    public DockerProcessBuilder volume(String hostSrc, String containerDest) {
        volumes.add(new AbstractMap.SimpleEntry<>(hostSrc, containerDest));
        return this;
    }

    public DockerProcessBuilder volume(String hostSrc, String containerDest, boolean readOnly) {
        volumes.add(new AbstractMap.SimpleEntry<>(hostSrc, containerDest + ":ro"));
        return this;
    }

    public DockerProcessBuilder cleanup(boolean cleanup) {
        this.cleanup = cleanup;
        return this;
    }

    public DockerProcessBuilder args(List<String> args) {
        this.args.addAll(args);
        return this;
    }

    public DockerProcessBuilder arg(String v) {
        this.args.add(v);
        return this;
    }

    public DockerProcessBuilder arg(String k, String v) {
        this.args.add(k);
        this.args.add(v);
        return this;
    }

    public DockerProcessBuilder env(Map<String, String> env) {
        this.env = env;
        return this;
    }

    public DockerProcessBuilder entryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
        return this;
    }

    public DockerProcessBuilder forcePull(boolean forcePull) {
        this.forcePull = forcePull;
        return this;
    }

    public DockerProcessBuilder useHostNetwork(boolean useHostNetwork) {
        this.useHostNetwork = useHostNetwork;
        return this;
    }

    public DockerProcessBuilder options(List<Map.Entry<String, String>> options) {
        this.options.addAll(options);
        return this;
    }

    public DockerProcessBuilder redirectErrorStream(boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
        return this;
    }

    private static String q(String s) {
        if (s == null) {
            return null;
        }

        return "'" + s + "'";
    }

    private static String env(String k, String defaultValue) {
        String s = System.getenv(k);
        return s != null ? s : defaultValue;
    }

    public static class DockerOptionsBuilder {

        private List<Map.Entry<String, String>> options = new ArrayList<>();

        public DockerOptionsBuilder etcHost(String host) {
            this.options.add(new AbstractMap.SimpleEntry<>("--add-host", host));
            return this;
        }

        public List<Map.Entry<String, String>> build() {
            return options;
        }
    }
}
