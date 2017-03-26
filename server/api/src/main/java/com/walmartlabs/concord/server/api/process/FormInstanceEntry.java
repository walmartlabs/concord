package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FormInstanceEntry implements Serializable {

    private final String processInstanceId;
    private final String formInstanceId;
    private final String name;
    private final List<Field> fields;

    @JsonCreator
    public FormInstanceEntry(@JsonProperty("processInstanceId") String processInstanceId,
                             @JsonProperty("formInstanceId") String formInstanceId,
                             @JsonProperty("name") String name,
                             @JsonProperty("fields") List<Field> fields) {

        this.processInstanceId = processInstanceId;
        this.formInstanceId = formInstanceId;
        this.name = name;
        this.fields = fields;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public String getFormInstanceId() {
        return formInstanceId;
    }

    public String getName() {
        return name;
    }

    public List<Field> getFields() {
        return fields;
    }

    public static class Field implements Serializable {

        private final String name;
        private final String label;
        private final String type;
        private final Cardinatity cardinatity;
        private final Object value;

        @JsonCreator
        public Field(String name, String label, String type, Cardinatity cardinatity, Object value) {
            this.name = name;
            this.label = label;
            this.type = type;
            this.cardinatity = cardinatity;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getLabel() {
            return label;
        }

        public String getType() {
            return type;
        }

        public Cardinatity getCardinatity() {
            return cardinatity;
        }

        public Object getValue() {
            return value;
        }
    }

    public enum Cardinatity {

        ONE_OR_NONE,
        ONE_AND_ONLY_ONE,
        AT_LEAST_ONE,
        ANY
    }
}
