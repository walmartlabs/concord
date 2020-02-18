package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class YamlValueType<T> {

    public static final YamlValueType<Object> NON_NULL = type("NON_NULL");

    public static final YamlValueType<Integer> INT = type("INT");
    public static final YamlValueType<Float> FLOAT = type("FLOAT");
    public static final YamlValueType<Boolean> BOOLEAN = type("BOOLEAN");
    public static final YamlValueType<String> STRING = type("STRING");
    public static final YamlValueType<Object> NULL = type("NULL");
    public static final YamlValueType<List<Serializable>> ARRAY = array("ARRAY", null);
    public static final YamlValueType<Map<String, Serializable>> OBJECT = map("OBJECT");

    public static final YamlValueType<FlowCall> FLOW_CALL = type("FLOW_CALL");
    public static final YamlValueType<TaskCall> TASK = type("TASK");
    public static final YamlValueType<Expression> EXPRESSION = type("EXPRESSION");
    public static final YamlValueType<Retry> RETRY = type("RETRY");
    public static final YamlValueType<Snapshot> SNAPSHOT = type("SNAPSHOT");
    public static final YamlValueType<ExitStep> EXIT = type("EXIT");
    public static final YamlValueType<Step> STEP = type("STEP");
    public static final YamlValueType<List<Step>> ARRAY_OF_STEP = array("ARRAY_OF_STEP", STEP);
    public static final YamlValueType<GroupOfSteps> TRY = type("TRY");
    public static final YamlValueType<GroupOfSteps> BLOCK = type("BLOCK");
    public static final YamlValueType<ParallelBlock> PARALLEL = type("PARALLEL");
    public static final YamlValueType<Forms> FORMS = type("FORMS");
    public static final YamlValueType<Form> FORM = type("FORM");
    public static final YamlValueType<FormField> FORM_FIELD = type("FORM_FIELD");
    public static final YamlValueType<List<FormField>> ARRAY_OF_FORM_FIELD = array("ARRAY_OF_FORM_FIELD", FORM_FIELD);
    public static final YamlValueType<FormCall> FORM_CALL = type("FORM_CALL");
    public static final YamlValueType<ImmutableFormCallOptions.Builder> FORM_CALL_FIELDS = type("ARRAY_OF_FORM_FIELD or EXPRESSION");
    public static final YamlValueType<ImmutableRetry.Builder> RETRY_TIMES = type("INT or EXPRESSION");
    public static final YamlValueType<ImmutableRetry.Builder> RETRY_DELAY = type("INT or EXPRESSION");

    private final String name;
    private final String description;

    private YamlValueType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public String toString() {
        return name + (description != null ? " (" + description + ")" : "");
    }

    private static <T> YamlValueType<T> type(String name) {
        return new YamlValueType<T>(name, null);
    }

    private static <T> YamlValueType<List<T>> array(String name, YamlValueType<T> arrayValueType) {
        return new YamlValueType<>(name, null);
    }

    private static <T> YamlValueType<Map<String, T>> map(String name) {
        return new YamlValueType<>(name, null);
    }
}
