package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

import java.util.Map;

public class YamlDockerStep extends YamlStep {

    private final String image;
    private final String cmd;
    private final Map<String, Object> env;

    public YamlDockerStep(JsonLocation location, String image, String cmd, Map<String, Object> env) {
        super(location);

        this.image = image;
        this.cmd = cmd;
        this.env = env;
    }

    public String getImage() {
        return image;
    }

    public String getCmd() {
        return cmd;
    }

    public Map<String, Object> getEnv() {
        return env;
    }
}
