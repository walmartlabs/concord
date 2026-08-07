package com.walmartlabs.concord.runtime.v25.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ConcordJsonSchemaGenerator25 {


    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected the output file path");
        }
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(Path.of(args[0]).toFile(), schema());
    }

    public static Map<String, Object> schema() {
        var schema = strictObject();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("$id", "https://concord.walmartlabs.com/schema/runtime-v2.5.json");
        schema.put("title", "Concord Runtime v2.5 Process Definition");
        schema.put("properties", properties(
                "configuration", ref("configuration"),
                "flows", ref("flows"),
                "publicFlows", ref("strings"),
                "profiles", ref("profiles"),
                "triggers", Map.of("type", "array", "items", ref("trigger")),
                "forms", ref("forms"),
                "imports", Map.of("type", "array", "items", ref("import")),
                "resources", ref("resources")));

        var definitions = new LinkedHashMap<String, Object>();
        definitions.put("data", Map.of());
        definitions.put("dataObject", Map.of("type", "object", "additionalProperties", ref("data")));
        definitions.put("nonNullData", Map.of("not", Map.of("type", "null")));
        definitions.put("string", Map.of("type", "string", "minLength", 1));
        definitions.put("strings", strings());
        definitions.put("configuration", configuration());
        definitions.put("eventConfiguration", eventConfiguration());
        definitions.put("exclusive", exclusive());
        definitions.put("validation", validation());
        definitions.put("flows", flows());
        definitions.put("flow", flow());
        definitions.put("step", step());
        definitions.put("retry", retry());
        definitions.put("loop", loop());
        definitions.put("out", out());
        definitions.put("runAs", strict(properties("withSecret", ref("string")), List.of("withSecret")));
        definitions.put("expression", Map.of("type", "string", "pattern", "^\\$\\{.*}$"));
        definitions.put("formField", formField());
        definitions.put("formFieldValue", formFieldValue());
        definitions.put("forms", forms());
        definitions.put("profiles", Map.of("type", "object", "propertyNames", Map.of("minLength", 1),
                "additionalProperties", ref("profile")));
        definitions.put("profile", profile());
        definitions.put("resources", strict(properties("concord", strings()), List.of()));
        definitions.put("trigger", trigger());
        definitions.put("genericTriggerOptions", genericTriggerOptions());
        definitions.put("githubConditions", githubConditions());
        definitions.put("githubExclusive", githubExclusive());
        definitions.put("import", importDefinition());
        definitions.put("secret", strict(properties(
                "org", ref("string"), "name", ref("string"), "password", ref("string")), List.of("name")));
        schema.put("$defs", definitions);
        return orderedMap(schema);
    }

    private static Map<String, Object> configuration() {
        return strict(properties(
                "runtime", Map.of("const", Definition25.RUNTIME_TYPE),
                "entryPoint", ref("string"),
                "arguments", ref("dataObject"),
                "dependencies", strings(),
                "extraDependencies", strings(),
                "debug", Map.of("type", "boolean"),
                "activeProfiles", strings(),
                "meta", ref("dataObject"),
                "events", ref("eventConfiguration"),
                "requirements", ref("dataObject"),
                "processTimeout", ref("string"),
                "suspendTimeout", ref("string"),
                "exclusive", ref("exclusive"),
                "out", strings(),
                "template", ref("string"),
                "parallelLoopParallelism", Map.of("type", "integer", "minimum", 1),
                "validation", ref("validation")), List.of());
    }

    private static Map<String, Object> eventConfiguration() {
        return strict(properties(
                "recordEvents", Map.of("type", "boolean"),
                "batchSize", Map.of("type", "integer", "minimum", 1),
                "batchFlushInterval", Map.of("type", "integer", "minimum", 1),
                "recordTaskInVars", Map.of("type", "boolean"),
                "truncateInVars", Map.of("type", "boolean"),
                "truncateMaxStringLength", Map.of("type", "integer", "minimum", 0),
                "truncateMaxArrayLength", Map.of("type", "integer", "minimum", 0),
                "truncateMaxDepth", Map.of("type", "integer", "minimum", 0),
                "recordTaskOutVars", Map.of("type", "boolean"),
                "truncateOutVars", Map.of("type", "boolean"),
                "updateMetaOnAllEvents", Map.of("type", "boolean"),
                "inVarsBlacklist", strings(),
                "outVarsBlacklist", strings(),
                "recordTaskMeta", Map.of("type", "boolean"),
                "truncateMeta", Map.of("type", "boolean"),
                "metaBlacklist", strings()), List.of());
    }

    private static Map<String, Object> exclusive() {
        return strict(properties(
                "group", ref("string"),
                "mode", caseInsensitiveEnum("cancel", "cancelOld", "wait")), List.of("group"));
    }

    private static Map<String, Object> validation() {
        return strict(properties("taskCalls", strict(properties(
                "in", caseInsensitiveEnum("DISABLED", "WARN", "FAIL"),
                "out", caseInsensitiveEnum("DISABLED", "WARN", "FAIL")), List.of())), List.of());
    }

    private static Map<String, Object> flows() {
        return Map.of("type", "object", "propertyNames", Map.of("minLength", 1), "additionalProperties", ref("flow"));
    }

    private static Map<String, Object> flow() {
        return Map.of("type", "array", "items", ref("step"), "minItems", 1);
    }

    private static Map<String, Object> step() {
        var alternatives = new ArrayList<Map<String, Object>>();
        alternatives.add(Map.of("enum", List.of("return", "exit")));
        alternatives.add(Map.of("type", "string", "pattern", "^\\$\\{.*}$"));
        alternatives.add(selector("log", ref("data"), properties("meta", ref("dataObject"), "name", ref("string")), List.of()));
        alternatives.add(selector("logYaml", ref("data"), properties("meta", ref("dataObject"), "name", ref("string")), List.of()));
        alternatives.add(selector("task", ref("string"), taskOptions(), List.of()));
        alternatives.add(selector("script", ref("string"), scriptOptions(), List.of()));
        alternatives.add(selector("expr", ref("string"), expressionOptions(), List.of()));
        alternatives.add(selector("call", ref("string"), callOptions(), List.of()));
        alternatives.add(selector("set", ref("dataObject"), properties("meta", ref("dataObject")), List.of()));
        alternatives.add(ifStep());
        alternatives.add(switchStep());
        alternatives.add(branchStep("try", properties(
                "out", ref("out"), "error", ref("flow"), "loop", ref("loop"),
                "meta", ref("dataObject"), "name", ref("string"))));
        alternatives.add(branchStep("block", properties(
                "out", ref("out"), "error", ref("flow"), "loop", ref("loop"),
                "meta", ref("dataObject"), "name", ref("string"))));
        alternatives.add(branchStep("parallel", properties("out", ref("out"), "meta", ref("dataObject"))));
        alternatives.add(selector("form", Map.of("oneOf", List.of(ref("expression"),
                        Map.of("type", "string", "pattern", "^[A-Za-z0-9_ $]+$"))), properties(
                "fields", Map.of("oneOf", List.of(Map.of("type", "array", "items", ref("formField")), ref("expression"))),
                "values", Map.of("oneOf", List.of(ref("dataObject"), ref("expression"))),
                "runAs", Map.of("oneOf", List.of(ref("dataObject"), ref("expression"))),
                "yield", Map.of("type", "boolean"), "saveSubmittedBy", Map.of("type", "boolean")), List.of()));
        alternatives.add(selector("checkpoint", Map.of("type", "string", "not", Map.of("const", "suspend")),
                properties("meta", ref("dataObject")), List.of()));
        alternatives.add(selector("suspend", ref("string"), properties("meta", ref("dataObject")), List.of()));
        alternatives.add(selector("throw", ref("data"), properties("meta", ref("dataObject"), "name", ref("string")), List.of()));
        alternatives.add(controlStep("return"));
        alternatives.add(controlStep("exit"));
        return Map.of("oneOf", alternatives);
    }

    private static Map<String, Object> taskOptions() {
        return properties("in", Map.of("oneOf", List.of(ref("dataObject"), ref("string"))),
                "out", ref("out"), "retry", ref("retry"), "error", ref("flow"), "loop", ref("loop"),
                "ignoreErrors", Map.of("type", "boolean"), "meta", ref("dataObject"), "name", ref("string"));
    }

    private static Map<String, Object> scriptOptions() {
        var result = taskOptions();
        result.remove("ignoreErrors");
        result.put("body", ref("string"));
        return result;
    }

    private static Map<String, Object> expressionOptions() {
        return properties("out", ref("out"), "error", ref("flow"), "meta", ref("dataObject"), "name", ref("string"));
    }

    private static Map<String, Object> callOptions() {
        return properties("in", Map.of("oneOf", List.of(ref("dataObject"), ref("string"))),
                "out", ref("out"), "retry", ref("retry"), "error", ref("flow"), "loop", ref("loop"),
                "meta", ref("dataObject"), "name", ref("string"));
    }

    private static Map<String, Object> ifStep() {
        var properties = properties("if", ref("string"), "then", ref("flow"), "else", ref("flow"), "meta", ref("dataObject"));
        return strict(properties, List.of("if", "then"));
    }

    private static Map<String, Object> switchStep() {
        var result = new LinkedHashMap<String, Object>();
        result.put("type", "object");
        result.put("properties", properties("switch", ref("string")));
        result.put("patternProperties", Map.of("^(?!(switch)$).+$", ref("flow")));
        result.put("required", List.of("switch"));
        result.put("additionalProperties", false);
        return result;
    }

    private static Map<String, Object> branchStep(String selector, Map<String, Object> options) {
        return selector(selector, Map.of("type", "array", "items", ref("step"), "minItems", 1), options, List.of());
    }

    private static Map<String, Object> controlStep(String selector) {
        return strict(properties(selector, Map.of("type", "null")), List.of(selector));
    }

    private static Map<String, Object> selector(String selector, Map<String, Object> value,
                                                Map<String, Object> options, List<String> required) {
        var properties = new LinkedHashMap<String, Object>();
        properties.put(selector, value);
        properties.putAll(options);
        var allRequired = new ArrayList<String>();
        allRequired.add(selector);
        allRequired.addAll(required);
        return strict(properties, allRequired);
    }

    private static Map<String, Object> retry() {
        return Map.of("oneOf", List.of(
                Map.of("type", "integer", "minimum", 0),
                ref("expression"),
                strict(properties(
                        "times", Map.of("oneOf", List.of(Map.of("type", "integer", "minimum", 0), ref("expression"))),
                        "delay", Map.of("oneOf", List.of(Map.of("type", "integer", "minimum", 0), ref("expression"))),
                        "in", ref("dataObject")), List.of())));
    }

    private static Map<String, Object> loop() {
        return strict(properties(
                "items", ref("nonNullData"),
                "mode", caseInsensitiveEnum("serial", "parallel"),
                "parallelism", Map.of("oneOf", List.of(Map.of("type", "integer", "minimum", 1), ref("expression")))),
                List.of("items"));
    }

    private static Map<String, Object> out() {
        return Map.of("oneOf", List.of(ref("string"), strings(), ref("dataObject")));
    }

    private static Map<String, Object> formField() {
        return Map.of("type", "object", "minProperties", 1, "maxProperties", 1,
                "additionalProperties", ref("formFieldValue"));
    }

    private static Map<String, Object> formFieldValue() {
        return Map.of("oneOf", List.of(ref("string"), Map.of(
                "type", "object",
                "properties", properties("type", ref("string"), "label", ref("string"),
                        "value", ref("data"), "default", ref("data"), "allow", ref("data")),
                "required", List.of("type"),
                "additionalProperties", ref("data"))));
    }

    private static Map<String, Object> forms() {
        return Map.of("type", "object", "propertyNames", Map.of("pattern", "^[A-Za-z0-9_ $]+$"),
                "additionalProperties", Map.of("oneOf", List.of(
                        Map.of("type", "array", "items", ref("formField")),
                        Map.of("type", "string", "pattern", "^\\$\\{.*}$"))));
    }

    private static Map<String, Object> profile() {
        return strict(properties("configuration", ref("configuration"), "flows", ref("flows"), "forms", ref("forms")), List.of());
    }

    private static Map<String, Object> trigger() {
        var generic = new LinkedHashMap<String, Object>();
        generic.put("type", "object");
        generic.put("minProperties", 1);
        generic.put("maxProperties", 1);
        generic.put("patternProperties", Map.of("^(?!(manual|cron|github|oneops)$).+$", ref("genericTriggerOptions")));
        generic.put("additionalProperties", false);

        return Map.of("oneOf", List.of(
                generic,
                trigger("manual", strict(properties(
                        "name", ref("string"), "exclusive", ref("exclusive"), "arguments", ref("dataObject"),
                        "activeProfiles", strings(), "entryPoint", ref("string")), List.of("entryPoint"))),
                trigger("cron", strict(properties(
                        "spec", ref("string"), "timezone", ref("string"), "runAs", ref("runAs"),
                        "exclusive", ref("exclusive"), "arguments", ref("dataObject"), "activeProfiles", strings(),
                        "entryPoint", ref("string")), List.of("spec", "entryPoint"))),
                trigger("github", strict(properties(
                        "version", Map.of("const", 2), "conditions", ref("githubConditions"),
                        "useInitiator", Map.of("type", "boolean"), "useEventCommitId", Map.of("type", "boolean"),
                        "ignoreEmptyPush", Map.of("type", "boolean"), "exclusive", ref("githubExclusive"),
                        "arguments", ref("dataObject"), "activeProfiles", strings(), "entryPoint", ref("string")),
                        List.of("version", "conditions", "entryPoint"))),
                trigger("oneops", strict(properties(
                        "version", Map.of("const", 2), "conditions", ref("dataObject"),
                        "useInitiator", Map.of("type", "boolean"), "exclusive", ref("exclusive"),
                        "arguments", ref("dataObject"), "activeProfiles", strings(), "entryPoint", ref("string")),
                        List.of("version", "conditions", "entryPoint")))));
    }

    private static Map<String, Object> trigger(String selector, Map<String, Object> options) {
        return strict(properties(selector, options), List.of(selector));
    }

    private static Map<String, Object> genericTriggerOptions() {
        return strict(properties(
                "version", Map.of("const", 2), "conditions", ref("dataObject"), "exclusive", ref("exclusive"),
                "arguments", ref("dataObject"), "activeProfiles", strings(), "entryPoint", ref("string")),
                List.of("version", "conditions", "entryPoint"));
    }

    private static Map<String, Object> githubConditions() {
        return strict(properties(
                "payload", ref("dataObject"), "type", ref("string"), "status", ref("string"), "branch", ref("string"),
                "githubOrg", ref("string"), "githubRepo", ref("string"), "githubHost", ref("string"),
                "sender", ref("string"), "repositoryInfo", Map.of("type", "array", "items", ref("dataObject")),
                "queryParams", ref("dataObject"), "files", githubFiles()), List.of("type"));
    }

    private static Map<String, Object> githubFiles() {
        var selector = Map.of("oneOf", List.of(ref("string"), strings()));
        return strict(properties("added", selector, "removed", selector, "modified", selector, "any", selector),
                List.of());
    }

    private static Map<String, Object> githubExclusive() {
        return strict(properties(
                "group", ref("string"), "groupBy", ref("string"),
                "mode", caseInsensitiveEnum("cancel", "cancelOld", "wait")), List.of());
    }
    private static Map<String, Object> importDefinition() {
        return Map.of("oneOf", List.of(
                trigger("mvn", strict(properties("url", ref("string"), "dest", ref("string")), List.of("url"))),
                trigger("dir", strict(properties("src", ref("string"), "dest", ref("string")), List.of("src"))),
                trigger("git", strict(properties(
                        "name", ref("string"), "url", ref("string"), "version", ref("string"), "path", ref("string"),
                        "dest", ref("string"), "secret", ref("secret"), "exclude", strings()), List.of()))));
    }

    private static Map<String, Object> strings() {
        return Map.of("type", "array", "items", ref("string"), "uniqueItems", true);
    }

    private static Map<String, Object> caseInsensitiveEnum(String... values) {
        var pattern = new StringBuilder("^(?:");
        for (var i = 0; i < values.length; i++) {
            if (i > 0) {
                pattern.append('|');
            }
            for (var character : values[i].toCharArray()) {
                if (Character.isLetter(character)) {
                    pattern.append('[').append(Character.toLowerCase(character))
                            .append(Character.toUpperCase(character)).append(']');
                } else {
                    pattern.append(character);
                }
            }
        }
        return Map.of("type", "string", "pattern", pattern.append(")$").toString());
    }

    private static Map<String, Object> strictObject() {
        var result = new LinkedHashMap<String, Object>();
        result.put("type", "object");
        result.put("additionalProperties", false);
        return result;
    }

    private static Map<String, Object> strict(Map<String, Object> properties, List<String> required) {
        var result = strictObject();
        result.put("properties", properties);
        if (!required.isEmpty()) {
            result.put("required", required);
        }
        return result;
    }

    private static Map<String, Object> orderedMap(Map<String, Object> source) {
        var result = new LinkedHashMap<String, Object>();
        for (var entry : new TreeMap<>(source).entrySet()) {
            result.put(entry.getKey(), orderedValue(entry.getValue()));
        }
        return result;
    }

    private static Object orderedValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var entries = new TreeMap<String, Object>();
            map.forEach((key, item) -> entries.put((String) key, item));
            return orderedMap(entries);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ConcordJsonSchemaGenerator25::orderedValue).toList();
        }
        return value;
    }

    private static Map<String, Object> ref(String name) {
        return Map.of("$ref", "#/$defs/" + name);
    }

    private static Map<String, Object> properties(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (var i = 0; i < entries.length; i += 2) {
            result.put((String) entries[i], entries[i + 1]);
        }
        return result;
    }

    private ConcordJsonSchemaGenerator25() {
    }
}
