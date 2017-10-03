package com.walmartlabs.concord.common;

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
    private boolean cleanup = true;
    private boolean debug;

    public DockerProcessBuilder(String image) {
        this.image = image;
    }

    public Process build() throws IOException {
        List<String> cmd = buildCmd();

        if (debug) {
            log.info("CMD: {}", cmd);
        }

        return new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
    }

    private List<String> buildCmd() {
        List<String> c = new ArrayList<>();
        c.add("docker");
        c.add("run");
        if (name != null) {
            c.add("--name");
            c.add(name);
        }
        if (cleanup) {
            c.add("--rm");
        }
        c.add("-i");
        if (volumes != null) {
            volumes.forEach(v -> {
                c.add("-v");
                c.add(v.getKey() + ":" + v.getValue());
            });
        }
        if (env != null) {
            env.forEach((k, v) -> {
                c.add("-e");
                c.add(k + "=" + v);
            });
        }
        if (workdir != null) {
            c.add("-w");
            c.add(workdir);
        }
        if (labels != null) {
            for (Map.Entry<String, String> l : labels.entrySet()) {
                String k = l.getKey();
                String v = l.getValue();

                c.add("--label");
                c.add(k + (v != null ? "=" + v : ""));
            }
        }
        c.add(image);
        c.addAll(args);
        return c;
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
}
