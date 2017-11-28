package com.walmartlabs.concord.server.api.org.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Arrays;

public class EncryptValueResponse implements Serializable {

    private final boolean ok = true;
    private final byte[] data;

    @JsonCreator
    public EncryptValueResponse(@JsonProperty("data") byte[] data) {
        this.data = data;
    }

    public boolean isOk() {
        return ok;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return "EncryptValueResponse{" +
                "ok=" + ok +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}
