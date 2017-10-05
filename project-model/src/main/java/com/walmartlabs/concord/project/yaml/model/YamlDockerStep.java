package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

import java.util.Map;

public class YamlDockerStep extends YamlStep {

    private final String image;
    private final String cmd;
    private final boolean forcePull;
    private final Map<String, Object> env;

    public YamlDockerStep(JsonLocation location, String image, String cmd, boolean forcePull, Map<String, Object> env) {
        super(location);

        this.image = image;
        this.cmd = cmd;
        this.forcePull = forcePull;
        this.env = env;
    }

    public String getImage() {
        return image;
    }

    public String getCmd() {
        return cmd;
    }

    public boolean isForcePull() {
        return forcePull;
    }

    public Map<String, Object> getEnv() {
        return env;
    }
}
