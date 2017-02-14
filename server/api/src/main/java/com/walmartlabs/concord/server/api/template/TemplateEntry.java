package com.walmartlabs.concord.server.api.template;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class TemplateEntry implements Serializable {

    private final String name;
    private final int size;

    @JsonCreator
    public TemplateEntry(@JsonProperty("name") String name,
                         @JsonProperty("size") int size) {

        this.name = name;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "TemplateEntry{" +
                "name='" + name + '\'' +
                ", size=" + size +
                '}';
    }
}
