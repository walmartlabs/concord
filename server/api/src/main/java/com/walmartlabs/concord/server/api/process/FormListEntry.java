package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public class FormListEntry implements Serializable {

    private final String formInstanceId;
    private final String name;
    private final boolean custom;
    private final boolean yield;

    @JsonCreator
    public FormListEntry(@JsonProperty("formInstanceId") String formInstanceId,
                         @JsonProperty("name") String name,
                         @JsonProperty("custom") boolean custom,
                         @JsonProperty("yield") boolean yield) {

        this.formInstanceId = formInstanceId;
        this.name = name;
        this.custom = custom;
        this.yield = yield;
    }

    public String getFormInstanceId() {
        return formInstanceId;
    }

    public String getName() {
        return name;
    }

    public boolean isCustom() {
        return custom;
    }

    public boolean isYield() {
        return yield;
    }

    @Override
    public String toString() {
        return "FormListEntry{" +
                "formInstanceId='" + formInstanceId + '\'' +
                ", name='" + name + '\'' +
                ", custom=" + custom +
                ", yield=" + yield +
                '}';
    }
}
