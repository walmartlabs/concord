package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

public class YamlDockerStep extends YamlStep {

    private final String image;
    private final String cmd;

    public YamlDockerStep(JsonLocation location, String image, String cmd) {
        super(location);

        this.image = image;
        this.cmd = cmd;
    }

    public String getImage() {
        return image;
    }

    public String getCmd() {
        return cmd;
    }
}
