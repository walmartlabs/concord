package com.walmartlabs.concord.server.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class VersionResponse implements Serializable {

    private final boolean ok = true;
    private final String version;

    @JsonCreator
    public VersionResponse(@JsonProperty("version") String version) {
        this.version = version;
    }

    public boolean isOk() {
        return ok;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "VersionResponse{" +
                "ok=" + ok +
                ", version='" + version + '\'' +
                '}';
    }
}
