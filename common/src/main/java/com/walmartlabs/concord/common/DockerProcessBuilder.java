package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import java.util.*;

public class DockerProcessBuilder {

    private static final Logger log = LoggerFactory.getLogger(DockerProcessBuilder.class);
    public static final String CONCORD_TX_ID_LABEL = "concordTxId";

    private final String image;

    private String name;
    private Map<String, String> labels;
    private String workdir;
    private List<String> args = new ArrayList<>();
    private Map<String, String> env;
    private List<AbstractMap.SimpleEntry<String, String>> volumes = new ArrayList<>();
    private String entryPoint;
    private boolean cleanup = true;
    private boolean debug = false;
    private boolean forcePull = true;

    public DockerProcessBuilder(String image) {
        this.image = image;
    }

    public Process build() throws IOException {
        String[] cmd;
        if (forcePull) {
            cmd = new String[]{"/bin/sh", "-c", "docker pull " + q(image) + " && " + buildCmd()};
        } else {
            cmd = new String[]{"/bin/sh", "-c", buildCmd()};
        }

        if (debug) {
            log.info("CMD: {}", (Object) cmd);
        }

        return new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
    }

    private String buildCmd() {
        List<String> c = new ArrayList<>();
        c.add("docker");
        c.add("run");
        if (name != null) {
            c.add("--name");
            c.add(q(name));
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
        c.add(q(image));
        if (args != null) {
            args.forEach(a -> c.add(q(a)));
        }
        return String.join(" ", c);
    }

    public DockerProcessBuilder name(String name) {
        this.name = name;
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

    private static String q(String s) {
        if (s == null) {
            return null;
        }

        return "'" + s + "'";
    }
}
