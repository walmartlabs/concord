package com.walmartlabs.concord.policyengine;

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
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TaskRule implements Serializable {

    private final String msg;
    private final String taskName;
    private final String method;
    private final List<Param> params;

    @JsonCreator
    public TaskRule(
            @JsonProperty("msg") String msg,
            @JsonProperty("name") String taskName,
            @JsonProperty("method") String method,
            @JsonProperty("params") List<Param> params) {

        this.msg = msg;
        this.taskName = taskName;
        this.method = method;
        this.params = Optional.ofNullable(params).orElse(Collections.emptyList());
    }

    public String getMsg() {
        return msg;
    }

    @JsonProperty("name")
    public String getTaskName() {
        return taskName;
    }

    public String getMethod() {
        return method;
    }

    public List<Param> getParams() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskRule taskRule = (TaskRule) o;
        return Objects.equals(msg, taskRule.msg) &&
                Objects.equals(taskName, taskRule.taskName) &&
                Objects.equals(method, taskRule.method) &&
                Objects.equals(params, taskRule.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, taskName, method, params);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, Utils.NotNullToStringStyle.NOT_NULL_STYLE)
                .append("msg", msg)
                .append("taskName", taskName)
                .append("method", method)
                .append("params", params)
                .toString();
    }

    public static class Param implements Serializable {

        private final int index;

        private final String name;

        private final boolean protectedVariable;

        private final List<Object> values;

        @JsonCreator
        public Param(
                @JsonProperty("index") int index,
                @JsonProperty("name") String name,
                @JsonProperty("protected") boolean protectedVariable,
                @JsonProperty("values") List<Object> values) {
            this.index = index;
            this.name = name;
            this.protectedVariable = protectedVariable;
            this.values = Optional.ofNullable(values).orElse(Collections.emptyList());
        }

        public int getIndex() {
            return index;
        }

        public String getName() {
            return name;
        }

        public boolean isProtected() {
            return protectedVariable;
        }

        public List<Object> getValues() {
            return values;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Param param = (Param) o;
            return index == param.index &&
                    protectedVariable == param.protectedVariable &&
                    Objects.equals(name, param.name) &&
                    Objects.equals(values, param.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, name, protectedVariable, values);
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this, Utils.NotNullToStringStyle.NOT_NULL_STYLE)
                    .append("index", index)
                    .append("name", name)
                    .append("protected", protectedVariable)
                    .append("values", values)
                    .toString();
        }
    }
}
