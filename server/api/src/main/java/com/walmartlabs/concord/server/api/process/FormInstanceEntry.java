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
        private final Cardinality cardinality;
        private final Object value;
        private final Object allowedValue;

        @JsonCreator
        public Field(String name, String label, String type, Cardinality cardinality, Object value, Object allowedValue) {
            this.name = name;
            this.label = label;
            this.type = type;
            this.cardinality = cardinality;
            this.value = value;
            this.allowedValue = allowedValue;
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

        public Cardinality getCardinality() {
            return cardinality;
        }

        public Object getValue() {
            return value;
        }

        public Object getAllowedValue() {
            return allowedValue;
        }
    }

    public enum Cardinality {

        ONE_OR_NONE,
        ONE_AND_ONLY_ONE,
        AT_LEAST_ONE,
        ANY
    }
}
