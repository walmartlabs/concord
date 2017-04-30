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

    @JsonCreator
    public FormListEntry(@JsonProperty("formInstanceId") String formInstanceId,
                         @JsonProperty("name") String name,
                         @JsonProperty("custom") boolean custom) {

        this.formInstanceId = formInstanceId;
        this.name = name;
        this.custom = custom;
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

    @Override
    public String toString() {
        return "FormListEntry{" +
                "formInstanceId='" + formInstanceId + '\'' +
                ", name='" + name + '\'' +
                ", custom=" + custom +
                '}';
    }
}
