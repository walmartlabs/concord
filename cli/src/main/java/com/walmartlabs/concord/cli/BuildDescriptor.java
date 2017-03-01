package com.walmartlabs.concord.cli;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Arrays;

public class BuildDescriptor implements Serializable {

    private final String[] files;
    private final String[] dependencies;

    @JsonCreator
    public BuildDescriptor(@JsonProperty("files") String[] files, @JsonProperty("deps") String[] dependencies) {
        this.files = files;
        this.dependencies = dependencies;
    }

    public String[] getFiles() {
        return files;
    }

    public String[] getDependencies() {
        return dependencies;
    }

    @Override
    public String toString() {
        return "BuildDescriptor{" +
                "files=" + Arrays.toString(files) +
                ", dependencies=" + Arrays.toString(dependencies) +
                '}';
    }
}
