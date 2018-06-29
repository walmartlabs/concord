package com.walmartlabs.concord.server.process;

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
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public class FormListEntry implements Serializable {

    private final String formInstanceId;
    private final String name;
    private final boolean custom;
    private final boolean yield;
    private final Map<String, Object> runAs;

    @JsonCreator
    public FormListEntry(@JsonProperty("formInstanceId") String formInstanceId,
                         @JsonProperty("name") String name,
                         @JsonProperty("custom") boolean custom,
                         @JsonProperty("yield") boolean yield,
                         @JsonProperty("runAs") Map<String, Object> runAs) {

        this.formInstanceId = formInstanceId;
        this.name = name;
        this.custom = custom;
        this.yield = yield;
        this.runAs = runAs;
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

    public Map<String, Object> getRunAs() {
        return runAs;
    }

    @Override
    public String toString() {
        return "FormListEntry{" +
                "formInstanceId='" + formInstanceId + '\'' +
                ", name='" + name + '\'' +
                ", custom=" + custom +
                ", yield=" + yield +
                ", runAs=" + runAs +
                '}';
    }
}
