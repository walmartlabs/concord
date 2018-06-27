package com.walmartlabs.concord.server.api.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class FormInstanceEntry implements Serializable {

    private final String processInstanceId;
    private final String formInstanceId;
    private final String name;
    private final List<Field> fields;
    private final boolean custom;
    private final boolean yield;

    @JsonCreator
    public FormInstanceEntry(@JsonProperty("processInstanceId") String processInstanceId,
                             @JsonProperty("formInstanceId") String formInstanceId,
                             @JsonProperty("name") String name,
                             @JsonProperty("fields") List<Field> fields,
                             @JsonProperty("custom") boolean custom,
                             @JsonProperty("yield") boolean yield) {

        this.processInstanceId = processInstanceId;
        this.formInstanceId = formInstanceId;
        this.name = name;
        this.fields = fields;
        this.custom = custom;
        this.yield = yield;
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

    public boolean isCustom() {
        return custom;
    }

    public boolean isYield() {
        return yield;
    }

    @Override
    public String toString() {
        return "FormInstanceEntry{" +
                "processInstanceId='" + processInstanceId + '\'' +
                ", formInstanceId='" + formInstanceId + '\'' +
                ", name='" + name + '\'' +
                ", fields=" + fields +
                ", custom=" + custom +
                ", yield=" + yield +
                '}';
    }

    @JsonInclude(Include.NON_NULL)
    public static class Field implements Serializable {

        private final String name;
        private final String label;
        private final String type;
        private final Cardinality cardinality;
        private final Object value;
        private final Object allowedValue;
        private final Map<String, Object> options;

        @JsonCreator
        public Field(String name, String label, String type, Cardinality cardinality, Object value, Object allowedValue, Map<String, Object> options) {
            this.name = name;
            this.label = label;
            this.type = type;
            this.cardinality = cardinality;
            this.value = value;
            this.allowedValue = allowedValue;
            this.options = Optional.ofNullable(options)
                    .map(m -> m.entrySet()
                            .stream()
                            .filter(e -> Objects.nonNull(e.getValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                    .orElse(null);
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

        public Map<String, Object> getOptions() {
            return options;
        }

        @Override
        public String toString() {
            return "Field{" +
                    "name='" + name + '\'' +
                    ", label='" + label + '\'' +
                    ", type='" + type + '\'' +
                    ", cardinality=" + cardinality +
                    ", value=" + value +
                    ", allowedValue=" + allowedValue +
                    ", options=" + options +
                    '}';
        }
    }

    public enum Cardinality {

        ONE_OR_NONE,
        ONE_AND_ONLY_ONE,
        AT_LEAST_ONE,
        ANY
    }
}
