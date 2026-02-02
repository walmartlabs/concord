package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.model.TaskCallValidation.ValidationMode;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class YamlValueType<T> {

    public static final YamlValueType<Object> NON_NULL = type("NON_NULL");

    public static final YamlValueType<Integer> INT = type("INT");
    public static final YamlValueType<Float> FLOAT = type("FLOAT");
    public static final YamlValueType<Boolean> BOOLEAN = type("BOOLEAN");
    public static final YamlValueType<String> STRING = type("STRING");
    public static final YamlValueType<String> NON_EMPTY_STRING = type("NON_EMPTY_STRING");
    public static final YamlValueType<String> PATTERN = type("PATTERN");
    public static final YamlValueType<Object> NULL = type("NULL");
    public static final YamlValueType<List<Serializable>> ARRAY = array("ARRAY", null);
    public static final YamlValueType<List<String>> ARRAY_OF_PATTERN = array("ARRAY_OF_PATTERN", PATTERN);
    public static final YamlValueType<List<String>> ARRAY_OF_STRING = array("ARRAY_OF_STRING", STRING);
    public static final YamlValueType<Map<String, Serializable>> OBJECT = map("OBJECT");
    public static final YamlValueType<Object> REGEXP_OR_ARRAY = type("REGEXP_OR_ARRAY");
    public static final YamlValueType<Duration> DURATION = type("ISO 8601 DURATION");
    public static final YamlValueType<String> TIMEZONE = type("TIMEZONE");
    public static final YamlValueType<Object> STRING_OR_ARRAY = type("STRING_OR_ARRAY");
    public static final YamlValueType<TaskCall> TASK = type("TASK");
    public static final YamlValueType<SetVariablesStep> SET_VARS = type("SET_VARIABLES");
    public static final YamlValueType<SuspendStep> SUSPEND = type("SUSPEND");
    public static final YamlValueType<ScriptCall> SCRIPT = type("SCRIPT");
    public static final YamlValueType<ImmutableScriptCallOptions.Builder> SCRIPT_CALL_IN = type("OBJECT or EXPRESSION");
    public static final YamlValueType<ImmutableScriptCallOptions.Builder> SCRIPT_CALL_OUT = type("STRING or OBJECT");
    public static final YamlValueType<Expression> EXPRESSION = type("EXPRESSION");
    public static final YamlValueType<String> EXPRESSION_VAL = type("EXPRESSION");
    public static final YamlValueType<Retry> RETRY = type("RETRY");
    public static final YamlValueType<Checkpoint> CHECKPOINT = type("CHECKPOINT");
    public static final YamlValueType<Step> STEP = type("STEP");
    public static final YamlValueType<List<Step>> ARRAY_OF_STEP = array("ARRAY_OF_STEP", STEP);
    public static final YamlValueType<GroupOfSteps> TRY = type("TRY");
    public static final YamlValueType<GroupOfSteps> BLOCK = type("BLOCK");
    public static final YamlValueType<ParallelBlock> PARALLEL = type("PARALLEL");
    public static final YamlValueType<Map<String, Form>> FORMS = type("FORMS");
    public static final YamlValueType<Form> FORM = type("FORM");
    public static final YamlValueType<FormField> FORM_FIELD = type("FORM_FIELD");
    public static final YamlValueType<List<FormField>> ARRAY_OF_FORM_FIELD = array("ARRAY_OF_FORM_FIELD", FORM_FIELD);
    public static final YamlValueType<FormCall> FORM_CALL = type("FORM_CALL");
    public static final YamlValueType<ImmutableFormCallOptions.Builder> FORM_CALL_FIELDS = type("ARRAY_OF_FORM_FIELD or EXPRESSION");
    public static final YamlValueType<ImmutableFormCallOptions.Builder> FORM_CALL_RUN_AS = type("OBJECT or EXPRESSION");
    public static final YamlValueType<ImmutableFormCallOptions.Builder> FORM_CALL_VALUES = type("OBJECT or EXPRESSION");
    public static final YamlValueType<ImmutableRetry.Builder> RETRY_TIMES = type("INT or EXPRESSION");
    public static final YamlValueType<ImmutableRetry.Builder> RETRY_DELAY = type("INT or EXPRESSION");
    public static final YamlValueType<Imports> IMPORTS = type("IMPORTS");
    public static final YamlValueType<Import> IMPORT = type("IMPORT");
    public static final YamlValueType<Import.GitDefinition> GIT_IMPORT = type("GIT_IMPORT");
    public static final YamlValueType<Import.MvnDefinition> MVN_IMPORT = type("MVN_IMPORT");
    public static final YamlValueType<Import.DirectoryDefinition> DIR_IMPORT = type("DIR_IMPORT");
    public static final YamlValueType<Import.SecretDefinition> IMPORT_SECRET = type("IMPORT_SECRET");
    public static final YamlValueType<Map<String, Flow>> FLOWS = type("FLOWS");
    public static final YamlValueType<KV<String, Flow>> FLOW = type("FLOW");
    public static final YamlValueType<FlowCall> FLOW_CALL = type("FLOW_CALL");
    public static final YamlValueType<ImmutableFlowCallOptions.Builder> FLOW_CALL_INPUT = type("OBJECT or EXPRESSION");
    public static final YamlValueType<ImmutableFlowCallOptions.Builder> FLOW_CALL_OUT = type("STRING or ARRAY_OF_STRING or OBJECT");
    public static final YamlValueType<Set<String>> PUBLIC_FLOWS = type("PUBLIC_FLOWS");
    public static final YamlValueType<Map<String, Profile>> PROFILES = type("PROFILES");
    public static final YamlValueType<KV<String, Profile>> PROFILE = type("PROFILE");
    public static final YamlValueType<ProcessDefinitionConfiguration> PROCESS_CFG = type("CONFIGURATION");
    public static final YamlValueType<Trigger> TRIGGER = type("TRIGGER");
    public static final YamlValueType<List<Trigger>> TRIGGERS = array("TRIGGER", TRIGGER);
    public static final YamlValueType<Trigger> GITHUB_TRIGGER = type("GITHUB_TRIGGER");
    public static final YamlValueType<Map<String, Object>> GITHUB_TRIGGER_CONDITIONS = type("GITHUB_TRIGGER_CONDITIONS");
    public static final YamlValueType<Trigger> CRON_TRIGGER = type("CRON_TRIGGER");
    public static final YamlValueType<Trigger> MANUAL_TRIGGER = type("MANUAL_TRIGGER");
    public static final YamlValueType<Trigger> ONEOPS_TRIGGER = type("ONEOPS_TRIGGER");
    public static final YamlValueType<Trigger> GENERIC_TRIGGER = type("GENERIC_TRIGGER");
    public static final YamlValueType<IfStep> IF = type("IF");
    public static final YamlValueType<SwitchStep> SWITCH = type("SWITCH");
    public static final YamlValueType<Resources> RESOURCES = type("RESOURCES");
    public static final YamlValueType<Map<String, Object>> RUN_AS = type("RUN_AS");
    public static final YamlValueType<ExclusiveMode> EXCLUSIVE_MODE = type("EXCLUSIVE_MODE");
    public static final YamlValueType<EventConfiguration> EVENTS_CFG = type("EVENTS_CONFIGURATION");
    public static final YamlValueType<ImmutableTaskCallOptions.Builder> TASK_CALL_IN = type("OBJECT or EXPRESSION");
    public static final YamlValueType<ImmutableTaskCallOptions.Builder> TASK_CALL_OUT = type("STRING or OBJECT");
    public static final YamlValueType<ImmutableExpressionOptions.Builder> EXPRESSION_CALL_OUT = type("STRING or OBJECT");
    public static final YamlValueType<ImmutableParallelBlockOptions.Builder> PARALLEL_BLOCK_OUT = type("STRING or ARRAY_OF_STRING or OBJECT");
    public static final YamlValueType<GithubTriggerExclusiveMode> GITHUB_EXCLUSIVE_MODE = type("GITHUB_EXCLUSIVE_MODE");
    public static final YamlValueType<Map<String, Object>> GITHUB_REPOSITORY_INFO = type("GITHUB_REPOSITORY_INFO");
    public static final YamlValueType<List<Map<String, Object>>> ARRAY_OF_GITHUB_REPOSITORY_INFO = array("REPOSITORY_INFO", GITHUB_REPOSITORY_INFO);
    public static final YamlValueType<Loop> LOOP = type("LOOP");
    public static final YamlValueType<ImmutableLoop.Builder> LOOP_PARALLELISM = type("int or expression");
    public static final YamlValueType<TaskCallValidation.ValidationMode> VALIDATION_MODE = type("VALIDATION_MODE");
    public static final YamlValueType<TaskCallValidation> TASK_CALL_VALIDATION = type("TASK_CALL_VALIDATION");
    public static final YamlValueType<ValidationConfiguration> VALIDATION_CFG = type("VALIDATION_CONFIGURATION");

    private final String name;

    private YamlValueType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    private static <T> YamlValueType<T> type(String name) {
        return new YamlValueType<>(name);
    }

    private static <T> YamlValueType<List<T>> array(String name, YamlValueType<T> arrayValueType) {
        return new YamlValueType<>(name);
    }

    private static <T> YamlValueType<Map<String, T>> map(String name) {
        return new YamlValueType<>(name);
    }
}
