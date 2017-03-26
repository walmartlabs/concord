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

    @JsonCreator
    public FormListEntry(@JsonProperty("formInstanceId") String formInstanceId,
                         @JsonProperty("name") String name) {

        this.formInstanceId = formInstanceId;
        this.name = name;
    }

    public String getFormInstanceId() {
        return formInstanceId;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "FormListEntry{" +
                "formInstanceId='" + formInstanceId + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
