package com.walmartlabs.concord.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DockerProcessBuilder {

    private static final Logger log = LoggerFactory.getLogger(DockerProcessBuilder.class);

    private final String image;
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
        c.add(image);
        c.addAll(args);
        return c;
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
