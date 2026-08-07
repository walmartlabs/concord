package com.walmartlabs.concord.runtime.v25.model.parser;

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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.runtime.model.Form;
import com.walmartlabs.concord.runtime.model.FormField;
import com.walmartlabs.concord.runtime.model.Trigger;
import com.walmartlabs.concord.runtime.v25.model.Configuration25;
import com.walmartlabs.concord.runtime.v25.model.Definition25;
import com.walmartlabs.concord.runtime.v25.model.Diagnostic;
import com.walmartlabs.concord.runtime.v25.model.Diagnostic.Severity;
import com.walmartlabs.concord.runtime.v25.model.Flow25;
import com.walmartlabs.concord.runtime.v25.model.ModelException;
import com.walmartlabs.concord.runtime.v25.model.Profile25;
import com.walmartlabs.concord.runtime.v25.model.SourceRange;
import com.walmartlabs.concord.runtime.v25.model.Step25;
import com.walmartlabs.concord.runtime.v25.model.Values;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DefinitionParser {

    private static final Set<String> TOP_LEVEL = Set.of(
            "configuration", "flows", "publicFlows", "profiles", "triggers", "forms", "imports", "resources");

    private static final Set<String> CONFIGURATION = Set.of(
            "runtime", "entryPoint", "arguments", "dependencies", "extraDependencies", "debug", "activeProfiles",
            "meta", "events", "requirements", "processTimeout", "suspendTimeout", "exclusive", "out", "template",
            "parallelLoopParallelism", "validation");

    private static final Set<String> SELECTORS = Set.of(
            "log", "logYaml", "task", "script", "expr", "call", "set", "if", "switch", "try", "block",
            "parallel", "form", "checkpoint", "suspend", "throw", "return", "exit");

    private static final Map<String, Set<String>> OPTIONS = options();

    private final YAMLFactory yamlFactory = new YAMLFactory();

    public Definition25 parse(Path baseDir, Path path) throws IOException {
        var normalizedBase = baseDir.toAbsolutePath().normalize();
        var normalizedPath = path.toAbsolutePath().normalize();
        var source = normalizedPath.startsWith(normalizedBase)
                ? normalizedBase.relativize(normalizedPath).toString().replace('\\', '/')
                : normalizedPath.toString();
        try (var input = Files.newInputStream(path)) {
            return parse(source, input);
        }
    }

    public Definition25 parse(String source, InputStream input) throws IOException {
        try (var parser = yamlFactory.createParser(input)) {
            var token = parser.nextToken();
            if (token == null) {
                throw new ModelException(List.of(new Diagnostic("V25_EMPTY_DOCUMENT", Severity.ERROR,
                        "The process definition is empty", new SourceRange(source, 1, 1, 1, 1), "$", null)));
            }
            var root = read(parser, source, new LinkedHashMap<>());
            if (parser.nextToken() != null) {
                throw new ModelException(List.of(new Diagnostic("V25_MULTIPLE_DOCUMENTS", Severity.ERROR,
                        "Only one YAML document is allowed per file", root.range(), "$", null)));
            }
            return new Decoder(source).definition(root);
        }
    }

    public Definition25 merge(List<Definition25> definitions) {
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("Definitions must not be empty");
        }
        var flows = new LinkedHashMap<String, Flow25>();
        var profiles = new LinkedHashMap<String, Profile25>();
        var triggers = new ArrayList<Trigger>();
        var imports = new ArrayList<Import>();
        var forms = new LinkedHashMap<String, Form>();
        var resources = new LinkedHashMap<String, Object>();
        var dependencies = new ArrayList<String>();
        var extraDependencies = new ArrayList<String>();
        var arguments = new LinkedHashMap<String, Object>();
        var rawFlows = new LinkedHashMap<String, Object>();
        var rawProfiles = new LinkedHashMap<String, Object>();
        var rawTriggers = new ArrayList<Object>();
        var rawImports = new ArrayList<Object>();
        var rawForms = new LinkedHashMap<String, Object>();

        for (var definition : definitions) {
            flows.putAll(definition.flows());
            profiles.putAll(definition.profiles());
            triggers.addAll(definition.triggers());
            imports.addAll(definition.imports().items());
            forms.putAll(definition.formDefinitions());
            resources = mergeResources(resources, definition.resources());
            dependencies = new ArrayList<>(Definition25.unionStrings(dependencies,
                    definition.configuration().dependencies()));
            extraDependencies = new ArrayList<>(Definition25.unionStrings(extraDependencies,
                    definition.configuration().extraDependencies()));
            arguments = Definition25.deepMerge(arguments, definition.configuration().arguments());
            rawFlows.putAll(map(definition.raw().get("flows")));
            rawProfiles.putAll(map(definition.raw().get("profiles")));
            rawTriggers.addAll(list(definition.raw().get("triggers")));
            rawImports.addAll(list(definition.raw().get("imports")));
            rawForms.putAll(map(definition.raw().get("forms")));
        }

        var base = definitions.get(definitions.size() - 1);
        var configuration = new LinkedHashMap<>(base.configuration().values());
        configuration.put("runtime", Definition25.RUNTIME_TYPE);
        configuration.put("arguments", arguments);
        configuration.put("dependencies", dependencies);
        configuration.put("extraDependencies", extraDependencies);

        var raw = new LinkedHashMap<>(base.raw());
        raw.put("configuration", configuration);
        raw.put("flows", rawFlows);
        raw.put("profiles", rawProfiles);
        raw.put("triggers", rawTriggers);
        raw.put("forms", rawForms);
        raw.put("imports", rawImports);
        raw.put("resources", resources);

        return new Definition25(new Configuration25(configuration, base.configuration().sourceRange()), flows,
                base.publicFlows(), profiles, triggers,
                Imports.of(imports), forms, resources, raw, base.importsRange());
    }

    public static Path resolveContained(Path root, String value, String path) {
        var normalizedRoot = root.toAbsolutePath().normalize();
        var relativeValue = value.startsWith("/") ? value.substring(1) : value;
        var resolved = normalizedRoot.resolve(relativeValue).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path at " + path + " escapes the project root");
        }
        return resolved;
    }

    private Node read(JsonParser parser, String source, Map<String, Node> anchors) throws IOException {
        var token = parser.currentToken();
        var start = parser.currentTokenLocation();
        if (parser instanceof YAMLParser yaml && yaml.isCurrentAlias()) {
            var name = parser.getText();
            var target = anchors.get(name);
            if (target == null) {
                throw new ModelException(List.of(new Diagnostic("V25_YAML_ALIAS", Severity.ERROR,
                        "Undefined YAML alias '*" + name + "'", range(source, start, parser.currentLocation()),
                        "$", null)));
            }
            return new AliasNode(target, range(source, start, parser.currentLocation()));
        }

        var anchor = parser instanceof YAMLParser yaml ? yaml.getCurrentAnchor() : null;
        Node result;
        if (token == JsonToken.START_OBJECT) {
            var fields = new ArrayList<Field>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    throw new IOException("Expected a mapping key at " + parser.currentLocation());
                }
                var fieldLocation = parser.currentTokenLocation();
                var name = parser.currentName();
                parser.nextToken();
                fields.add(new Field(name, range(source, fieldLocation, fieldLocation), read(parser, source, anchors)));
            }
            result = new MappingNode(fields, range(source, start, parser.currentLocation()));
        } else if (token == JsonToken.START_ARRAY) {
            var values = new ArrayList<Node>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                values.add(read(parser, source, anchors));
            }
            result = new SequenceNode(values, range(source, start, parser.currentLocation()));
        } else {
            var value = switch (token) {
                case VALUE_STRING -> parser.getText();
                case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getNumberValue();
                case VALUE_TRUE -> Boolean.TRUE;
                case VALUE_FALSE -> Boolean.FALSE;
                case VALUE_NULL -> null;
                default -> throw new IOException("Unsupported YAML token " + token + " at " + parser.currentLocation());
            };
            result = new ScalarNode(value, range(source, start, parser.currentLocation()));
        }
        if (anchor != null) {
            anchors.put(anchor, result);
        }
        return result;
    }

    private static SourceRange range(String source, JsonLocation start, JsonLocation end) {
        return new SourceRange(source, Math.max(1, start.getLineNr()), Math.max(1, start.getColumnNr()),
                Math.max(1, end.getLineNr()), Math.max(1, end.getColumnNr()));
    }

    private static LinkedHashMap<String, Object> mergeResources(Map<String, Object> first, Map<String, Object> second) {
        var result = new LinkedHashMap<String, Object>(first);
        var firstConcord = strings(first.get("concord"));
        var secondConcord = strings(second.get("concord"));
        result.putAll(second);
        result.put("concord", Definition25.unionStrings(firstConcord, secondConcord));
        return result;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().map(Object::toString).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return (Map<String, Object>) map;
    }

    private static List<Object> list(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return new ArrayList<>(list);
    }

    private static Map<String, Set<String>> options() {
        var result = new LinkedHashMap<String, Set<String>>();
        result.put("task", Set.of("in", "out", "retry", "error", "loop", "ignoreErrors", "meta", "name"));
        result.put("log", Set.of("meta", "name"));
        result.put("logYaml", Set.of("meta", "name"));
        result.put("script", Set.of("body", "in", "out", "retry", "error", "loop", "meta", "name"));
        result.put("expr", Set.of("out", "error", "meta", "name"));
        result.put("call", Set.of("in", "out", "retry", "error", "loop", "meta", "name"));
        result.put("set", Set.of("meta"));
        result.put("if", Set.of("then", "else", "meta"));
        result.put("switch", Set.of("meta"));
        result.put("try", Set.of("out", "error", "loop", "meta", "name"));
        result.put("block", Set.of("out", "error", "loop", "meta", "name"));
        result.put("parallel", Set.of("out", "meta"));
        result.put("form", Set.of("fields", "values", "runAs", "yield", "saveSubmittedBy"));
        result.put("checkpoint", Set.of("meta"));
        result.put("suspend", Set.of("meta"));
        result.put("throw", Set.of("meta", "name"));
        result.put("return", Set.of());
        result.put("exit", Set.of());
        return Collections.unmodifiableMap(result);
    }

    private final class Decoder {

        private final String source;
        private final List<Diagnostic> diagnostics = new ArrayList<>();

        private Decoder(String source) {
            this.source = source;
        }

        private Definition25 definition(Node node) {
            duplicates(node, "$");
            var root = mapping(node, "$", "The document root must be a mapping");
            var index = index(root, "$", TOP_LEVEL);
            unknown(index, TOP_LEVEL, "$", false);

            var configuration = configuration(index.get("configuration"), "configuration");
            var flows = flows(index.get("flows"), "flows");
            var publicFlows = stringSet(index.get("publicFlows"), "publicFlows");
            var profiles = profiles(index.get("profiles"), "profiles");
            var triggers = triggers(index.get("triggers"), "triggers");
            var forms = forms(index.get("forms"), "forms");
            var imports = imports(index.get("imports"), "imports");
            var resources = resources(index.get("resources"), "resources");

            failIfErrors();
            var raw = root.value();
            return new Definition25(configuration, flows, publicFlows, profiles, triggers, imports, forms, resources,
                    raw, index.get("imports") == null ? null : index.get("imports").value().range());
        }

        private Configuration25 configuration(Field field, String path) {
            if (field == null) {
                return new Configuration25(Map.of("runtime", Definition25.RUNTIME_TYPE));
            }
            var node = mapping(field.value(), path, "configuration must be a mapping");
            var index = index(node, path, CONFIGURATION);
            unknown(index, CONFIGURATION, path, false);
            var result = new LinkedHashMap<String, Object>();
            index.forEach((name, value) -> result.put(name, value.value().value()));
            var runtime = result.get("runtime");
            if (runtime != null && !Definition25.RUNTIME_TYPE.equals(runtime.toString())) {
                error("V25_RUNTIME", "configuration.runtime must be '" + Definition25.RUNTIME_TYPE + "'",
                        index.get("runtime").value().range(), path + ".runtime", null);
            }
            result.put("runtime", Definition25.RUNTIME_TYPE);
            validateConfiguration(index, path);
            requireMapping(result.get("arguments"), index.get("arguments"), path + ".arguments");
            normalizeValidation(result, index.get("validation"), path + ".validation");
            return new Configuration25(result, field.value().range());
        }

        private void validateConfiguration(Map<String, Field> index, String path) {
            for (var name : List.of("entryPoint", "processTimeout", "suspendTimeout", "template")) {
                requireString(index.get(name), path + "." + name);
            }
            requireBoolean(index.get("debug"), path + ".debug");
            requireStringList(index.get("dependencies"), path + ".dependencies");
            requireStringList(index.get("extraDependencies"), path + ".extraDependencies");
            requireStringList(index.get("activeProfiles"), path + ".activeProfiles");
            requireStringList(index.get("out"), path + ".out");
            for (var name : List.of("arguments", "meta", "requirements")) {
                var field = index.get(name);
                requireMapping(field != null ? field.value().value() : null, field, path + "." + name);
            }
            validateExclusive(index.get("exclusive"), path + ".exclusive");
            validateEvents(index.get("events"), path + ".events");
            var parallelism = index.get("parallelLoopParallelism");
            if (parallelism != null && !isPositiveInt(parallelism.value())) {
                error("V25_CONFIGURATION", "parallelLoopParallelism must be a positive integer",
                        parallelism.value().range(), path + ".parallelLoopParallelism", null);
            }
        }

        private void validateExclusive(Field field, String path) {
            if (field == null) {
                return;
            }
            var options = index(mapping(field.value(), path, "exclusive must be a mapping"), path,
                    Set.of("group", "mode"));
            unknown(options, Set.of("group", "mode"), path, false);
            var group = options.get("group");
            if (group == null) {
                error("V25_REQUIRED", "exclusive requires 'group'", field.value().range(), path, "group");
            } else {
                requireString(group, path + ".group");
            }
            validateExclusiveMode(options.get("mode"), path + ".mode");
        }

        private void validateEvents(Field field, String path) {
            if (field == null) {
                return;
            }
            var booleans = Set.of("recordEvents", "recordTaskInVars", "truncateInVars", "recordTaskOutVars",
                    "truncateOutVars", "updateMetaOnAllEvents", "recordTaskMeta", "truncateMeta");
            var integers = Set.of("batchFlushInterval", "batchSize", "truncateMaxStringLength",
                    "truncateMaxArrayLength", "truncateMaxDepth");
            var strings = Set.of("inVarsBlacklist", "outVarsBlacklist", "metaBlacklist");
            var allowed = new LinkedHashSet<String>();
            allowed.addAll(booleans);
            allowed.addAll(integers);
            allowed.addAll(strings);
            var options = index(mapping(field.value(), path, "events must be a mapping"), path, allowed);
            unknown(options, allowed, path, false);
            booleans.forEach(name -> requireBoolean(options.get(name), path + "." + name));
            strings.forEach(name -> requireStringList(options.get(name), path + "." + name));
            integers.forEach(name -> validateEventInteger(options.get(name), path + "." + name,
                    Set.of("batchFlushInterval", "batchSize").contains(name) ? 1 : 0));
        }

        private Map<String, Flow25> flows(Field field, String path) {
            if (field == null) {
                return Map.of();
            }
            var node = mapping(field.value(), path, "flows must be a mapping");
            var index = index(node, path, null);
            var result = new LinkedHashMap<String, Flow25>();
            index.forEach((name, value) -> {
                if (name.isBlank()) {
                    error("V25_FLOW_NAME", "Flow names must not be empty", value.range(), path, null);
                }
                var steps = steps(value.value(), path + "." + name);
                if (steps.isEmpty()) {
                    error("V25_EMPTY_FLOW", "A flow requires a non-empty step list", value.value().range(),
                            path + "." + name, null);
                }
                result.put(name, new Flow25(name, steps, value.value().range()));
            });
            return result;
        }

        private Map<String, Profile25> profiles(Field field, String path) {
            if (field == null) {
                return Map.of();
            }
            var node = mapping(field.value(), path, "profiles must be a mapping");
            var result = new LinkedHashMap<String, Profile25>();
            index(node, path, null).forEach((name, value) -> {
                var profilePath = path + "." + name;
                var profileNode = mapping(value.value(), profilePath, "A profile must be a mapping");
                var profileIndex = index(profileNode, profilePath, Set.of("configuration", "flows", "forms"));
                unknown(profileIndex, Set.of("configuration", "flows", "forms"), profilePath, false);
                result.put(name, new Profile25(configuration(profileIndex.get("configuration"),
                        profilePath + ".configuration"), Set.of(), flows(profileIndex.get("flows"),
                        profilePath + ".flows"), forms(profileIndex.get("forms"), profilePath + ".forms"),
                        value.value().range()));
            });
            return result;
        }

        private List<Trigger> triggers(Field field, String path) {
            if (field == null) {
                return List.of();
            }
            var node = sequence(field.value(), path, "triggers must be a list");
            var result = new ArrayList<Trigger>();
            for (var i = 0; i < node.values().size(); i++) {
                var triggerPath = path + "[" + i + "]";
                var triggerNode = mapping(node.values().get(i), triggerPath, "A trigger must be a mapping");
                var triggerIndex = index(triggerNode, triggerPath, null);
                if (triggerIndex.size() != 1) {
                    error("V25_TRIGGER_SELECTOR", "A trigger must contain exactly one provider selector",
                            triggerNode.range(), triggerPath, null);
                    continue;
                }
                var provider = triggerIndex.values().iterator().next();
                var providerPath = triggerPath + "." + provider.name();
                var optionsNode = mapping(provider.value(), providerPath, "Trigger options must be a mapping");
                var optionIndex = index(optionsNode, providerPath, triggerOptions(provider.name()));
                unknown(optionIndex, triggerOptions(provider.name()), providerPath, false);
                validateTrigger(provider, optionIndex, providerPath);

                var options = optionsNode.value();
                var version = options.get("version");
                var builder = Trigger.builder().name(provider.name()).sourceMap(provider.value().range().toSourceMap());
                putTriggerMap(builder::arguments, options.get("arguments"));
                var activeProfiles = strings(options.get("activeProfiles"));
                if (!activeProfiles.isEmpty()) {
                    builder.activeProfiles(activeProfiles);
                }
                if ("cron".equals(provider.name())) {
                    var configuration = new LinkedHashMap<String, Object>();
                    for (var key : List.of("entryPoint", "runAs", "exclusive")) {
                        if (options.containsKey(key)) {
                            configuration.put(key, options.get(key));
                        }
                    }
                    var conditions = new LinkedHashMap<String, Object>();
                    for (var key : List.of("spec", "timezone")) {
                        if (options.containsKey(key)) {
                            conditions.put(key, options.get(key));
                        }
                    }
                    builder.configuration(configuration).conditions(conditions);
                } else {
                    var conditions = new LinkedHashMap<String, Object>();
                    if (options.get("conditions") instanceof Map<?, ?> configuredConditions) {
                        configuredConditions.forEach((key, value) -> conditions.put(String.valueOf(key), value));
                    }
                    var configuration = new LinkedHashMap<>(options);
                    configuration.remove("arguments");
                    configuration.remove("activeProfiles");
                    configuration.remove("conditions");
                    if (Set.of("github", "oneops").contains(provider.name())) {
                        conditions.put("version", version);
                        configuration.remove("version");
                    }
                    builder.conditions(conditions).configuration(configuration);
                }
                result.add(builder.build());
            }
            return result;
        }

        private Set<String> triggerOptions(String provider) {
            return switch (provider) {
                case "manual" -> Set.of("name", "exclusive", "arguments", "activeProfiles", "entryPoint");
                case "cron" -> Set.of("spec", "timezone", "runAs", "exclusive", "arguments", "activeProfiles",
                        "entryPoint");
                case "github" -> Set.of("version", "conditions", "useInitiator", "useEventCommitId",
                        "ignoreEmptyPush", "exclusive", "arguments", "activeProfiles", "entryPoint");
                case "oneops" -> Set.of("version", "conditions", "useInitiator", "exclusive", "arguments",
                        "activeProfiles", "entryPoint");
                default -> Set.of("version", "conditions", "exclusive", "arguments", "activeProfiles",
                        "entryPoint");
            };
        }

        private void validateTrigger(Field provider, Map<String, Field> options, String path) {
            var name = provider.name();
            requireString(options.get("entryPoint"), path + ".entryPoint");
            if (!options.containsKey("entryPoint")) {
                error("V25_REQUIRED", "Trigger requires 'entryPoint'", provider.value().range(), path, "entryPoint");
            }
            requireMapping(options.get("arguments") == null ? null : options.get("arguments").value().value(),
                    options.get("arguments"), path + ".arguments");
            requireStringList(options.get("activeProfiles"), path + ".activeProfiles");

            if ("manual".equals(name)) {
                requireString(options.get("name"), path + ".name");
                validateExclusive(options.get("exclusive"), path + ".exclusive");
                return;
            }
            if ("cron".equals(name)) {
                var spec = options.get("spec");
                if (spec == null) {
                    error("V25_REQUIRED", "cron trigger requires 'spec'", provider.value().range(), path, "spec");
                } else {
                    requireString(spec, path + ".spec");
                }
                requireString(options.get("timezone"), path + ".timezone");
                validateRunAs(options.get("runAs"), path + ".runAs");
                validateExclusive(options.get("exclusive"), path + ".exclusive");
                return;
            }

            var version = options.get("version");
            if (version == null || !(version.value() instanceof ScalarNode scalar)
                    || !isVersionTwo(scalar.value())) {
                error("V25_TRIGGER_VERSION", "Trigger version must be the integer 2",
                        version == null ? provider.value().range() : version.value().range(), path + ".version", null);
            }
            if (!options.containsKey("conditions")) {
                error("V25_REQUIRED", name + " trigger requires 'conditions'", provider.value().range(), path,
                        "conditions");
            }
            requireMapping(options.get("conditions") == null ? null : options.get("conditions").value().value(),
                    options.get("conditions"), path + ".conditions");
            if ("github".equals(name)) {
                if (options.containsKey("conditions")) {
                    validateGithubConditions(options.get("conditions").value(), path + ".conditions");
                }
                requireBoolean(options.get("useInitiator"), path + ".useInitiator");
                requireBoolean(options.get("useEventCommitId"), path + ".useEventCommitId");
                requireBoolean(options.get("ignoreEmptyPush"), path + ".ignoreEmptyPush");
                validateGithubExclusive(options.get("exclusive"), path + ".exclusive");
            } else {
                requireBoolean(options.get("useInitiator"), path + ".useInitiator");
                validateExclusive(options.get("exclusive"), path + ".exclusive");
            }
        }

        private boolean isVersionTwo(Object value) {
            if (!(value instanceof Number number)) {
                return false;
            }
            try {
                return new java.math.BigDecimal(number.toString()).compareTo(java.math.BigDecimal.valueOf(2)) == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private void validateRunAs(Field field, String path) {
            if (field == null) {
                return;
            }
            var options = index(mapping(field.value(), path, "runAs must be a mapping"), path, Set.of("withSecret"));
            unknown(options, Set.of("withSecret"), path, false);
            requireString(options.get("withSecret"), path + ".withSecret");
            if (!options.containsKey("withSecret")) {
                error("V25_REQUIRED", "runAs requires 'withSecret'", field.value().range(), path, "withSecret");
            }
        }

        private void validateGithubExclusive(Field field, String path) {
            if (field == null) {
                return;
            }
            var allowed = Set.of("group", "groupBy", "mode");
            var options = index(mapping(field.value(), path, "exclusive must be a mapping"), path, allowed);
            unknown(options, allowed, path, false);
            var group = options.get("group");
            var groupBy = options.get("groupBy");
            if ((group == null) == (groupBy == null)) {
                error("V25_REQUIRED", "exclusive requires exactly one of 'group' or 'groupBy'",
                        field.value().range(), path, group == null ? "group" : "groupBy");
            }
            requireString(group, path + ".group");
            requireString(groupBy, path + ".groupBy");
            validateExclusiveMode(options.get("mode"), path + ".mode");
        }

        private void validateGithubConditions(Node node, String path) {
            var allowed = Set.of("payload", "type", "status", "branch", "githubOrg", "githubRepo", "githubHost",
                    "sender", "repositoryInfo", "queryParams", "files");
            var options = index(mapping(node, path, "github conditions must be a mapping"), path, allowed);
            unknown(options, allowed, path, false);
            var type = options.get("type");
            if (type == null) {
                error("V25_REQUIRED", "github conditions require 'type'", node.range(), path, "type");
            } else {
                requireString(type, path + ".type");
            }
            for (var name : List.of("status", "branch", "githubOrg", "githubRepo", "githubHost", "sender")) {
                requireString(options.get(name), path + "." + name);
            }
            requireMapping(options.get("payload") == null ? null : options.get("payload").value().value(),
                    options.get("payload"), path + ".payload");
            requireMapping(options.get("queryParams") == null ? null : options.get("queryParams").value().value(),
                    options.get("queryParams"), path + ".queryParams");
            validateRepositoryInfo(options.get("repositoryInfo"), path + ".repositoryInfo");
            validateGithubFiles(options.get("files"), path + ".files");
        }

        private void validateRepositoryInfo(Field field, String path) {
            if (field == null) {
                return;
            }
            var values = sequence(field.value(), path, "repositoryInfo must be a list");
            for (var i = 0; i < values.values().size(); i++) {
                mapping(values.values().get(i), path + "[" + i + "]", "repositoryInfo values must be mappings");
            }
        }

        private void validateGithubFiles(Field field, String path) {
            if (field == null) {
                return;
            }
            var allowed = Set.of("added", "removed", "modified", "any");
            var options = index(mapping(field.value(), path, "files must be a mapping"), path, allowed);
            unknown(options, allowed, path, false);
            options.forEach((name, value) -> {
                if (value.value() instanceof ScalarNode scalar && scalar.value() instanceof String) {
                    return;
                }
                requireStringList(value, path + "." + name);
            });
        }

        private Map<String, Form> forms(Field field, String path) {
            if (field == null) {
                return Map.of();
            }
            var node = mapping(field.value(), path, "forms must be a mapping");
            var result = new LinkedHashMap<String, Form>();
            index(node, path, null).forEach((name, value) -> {
                if (!name.matches("^[A-Za-z0-9_ $]+$")) {
                    error("V25_FORM_NAME", "Form name must match ^[A-Za-z0-9_ $]+$", value.range(),
                            path + "." + name, null);
                }
                if (value.value() instanceof SequenceNode fields) {
                    var parsedFields = new ArrayList<FormField>();
                    for (var i = 0; i < fields.values().size(); i++) {
                        var parsed = formField(fields.values().get(i), path + "." + name + "[" + i + "]");
                        if (parsed != null) {
                            parsedFields.add(parsed);
                        }
                    }
                    result.put(name, new com.walmartlabs.concord.runtime.v25.model.Form25(name, parsedFields, null,
                            value.value().range()));
                } else if (value.value() instanceof ScalarNode scalar && scalar.value() instanceof String expression
                        && isExpression(expression)) {
                    result.put(name, new com.walmartlabs.concord.runtime.v25.model.Form25(name, List.of(), expression,
                            value.value().range()));
                } else {
                    error("V25_FORM_SHAPE", "A form must be a field list or a pure expression", value.value().range(),
                            path + "." + name, null);
                }
            });
            return result;
        }

        private FormField formField(Node node, String path) {
            var fieldNode = mapping(node, path, "A form field must be a mapping");
            var fieldIndex = index(fieldNode, path, null);
            if (fieldIndex.size() != 1) {
                error("V25_FORM_FIELD", "A form field must contain exactly one field name", fieldNode.range(), path, null);
                return null;
            }
            var field = fieldIndex.values().iterator().next();
            var builder = FormField.builder().name(field.name()).location(field.value().range().toSourceMap());
            if (field.value() instanceof ScalarNode scalar) {
                if (!(scalar.value() instanceof String type)) {
                    error("V25_FORM_TYPE", "A form field type must be a string", scalar.range(), path, null);
                    return null;
                }
                builder.type(type);
                return builder.build();
            }
            var optionsNode = mapping(field.value(), path + "." + field.name(), "Form field options must be a mapping");
            var options = optionsNode.value();
            var type = options.get("type");
            if (!(type instanceof String)) {
                error("V25_FORM_TYPE", "A form field requires a string 'type'", field.value().range(), path, null);
                return null;
            }
            builder.type(type.toString());
            if (options.get("label") != null) {
                builder.label(options.get("label").toString());
            }
            var defaultValue = options.containsKey("value") ? options.get("value") : options.get("default");
            putSerializable(builder::defaultValue, defaultValue);
            putSerializable(builder::allowedValue, options.get("allow"));
            var commonOptions = new LinkedHashMap<String, Serializable>();
            options.forEach((key, item) -> {
                if (!Set.of("type", "label", "value", "default", "allow").contains(key)
                        && item instanceof Serializable serializable) {
                    commonOptions.put(key, serializable);
                }
            });
            builder.options(commonOptions);
            return builder.build();
        }

        private Imports imports(Field field, String path) {
            if (field == null) {
                return Imports.of(List.of());
            }
            var node = sequence(field.value(), path, "imports must be a list");
            var result = new ArrayList<Import>();
            for (var i = 0; i < node.values().size(); i++) {
                var importPath = path + "[" + i + "]";
                var importNode = mapping(node.values().get(i), importPath, "An import must be a mapping");
                var importIndex = index(importNode, importPath, Set.of("mvn", "git", "dir"));
                unknown(importIndex, Set.of("mvn", "git", "dir"), importPath, false);
                if (importIndex.size() != 1
                        || !Set.of("mvn", "git", "dir").contains(importIndex.keySet().iterator().next())) {
                    error("V25_IMPORT_SELECTOR", "An import must contain exactly one of mvn, git, or dir",
                            importNode.range(), importPath, null);
                    continue;
                }
                var selected = importIndex.values().iterator().next();
                var optionsNode = mapping(selected.value(), importPath + "." + selected.name(),
                        "Import options must be a mapping");
                var allowed = switch (selected.name()) {
                    case "mvn" -> Set.of("url", "dest");
                    case "git" -> Set.of("name", "url", "version", "path", "dest", "exclude", "secret");
                    case "dir" -> Set.of("src", "dest");
                    default -> Set.<String>of();
                };
                var optionIndex = index(optionsNode, importPath + "." + selected.name(), allowed);
                unknown(optionIndex, allowed, importPath + "." + selected.name(), false);
                if ("git".equals(selected.name())) {
                    validateImportSecret(optionIndex.get("secret"), importPath + ".git.secret");
                    requireStringList(optionIndex.get("exclude"), importPath + ".git.exclude");
                }
                var options = optionsNode.value();
                switch (selected.name()) {
                    case "mvn" -> addMavenImport(result, options, selected.value().range(), importPath);
                    case "git" -> addGitImport(result, options, selected.value().range(), importPath);
                    case "dir" -> addDirectoryImport(result, options, selected.value().range(), importPath);
                    default -> throw new IllegalStateException("Unsupported import selector " + selected.name());
                }
            }
            return Imports.of(result);
        }

        private Map<String, Object> resources(Field field, String path) {
            if (field == null) {
                return Map.of("concord", List.of("glob:concord/{**/,}{*.,}concord.{yml,yaml}"));
            }
            var node = mapping(field.value(), path, "resources must be a mapping");
            var index = index(node, path, Set.of("concord"));
            unknown(index, Set.of("concord"), path, false);
            requireStringList(index.get("concord"), path + ".concord");
            var result = new LinkedHashMap<String, Object>();
            index.forEach((name, value) -> result.put(name, value.value().value()));
            if (!result.containsKey("concord")) {
                result.put("concord", List.of("glob:concord/{**/,}{*.,}concord.{yml,yaml}"));
            }
            return result;
        }

        private List<Step25> steps(Node node, String path) {
            var sequence = sequence(node, path, "A flow or branch must be a list of steps");
            var result = new ArrayList<Step25>();
            for (var i = 0; i < sequence.values().size(); i++) {
                var step = step(sequence.values().get(i), path + "[" + i + "]");
                if (step != null) {
                    result.add(step);
                }
            }
            return result;
        }

        private Step25 step(Node node, String path) {
            if (node instanceof ScalarNode scalar) {
                if ("return".equals(scalar.value()) || "exit".equals(scalar.value())) {
                    return new Step25(scalar.value().toString(), null, Map.of(), Map.of(), scalar.range(), path);
                }
                if (scalar.value() instanceof String value && value.startsWith("${") && value.endsWith("}")) {
                    return new Step25("expr", value, Map.of(), Map.of(), scalar.range(), path);
                }
                error("V25_STEP_SHAPE", "A scalar step must be return, exit, or a pure expression",
                        scalar.range(), path, null);
                return null;
            }
            var stepNode = mapping(node, path, "A step must be a mapping");
            var index = index(stepNode, path, null);
            var type = selector(index, stepNode, path);
            if (type == null) {
                return null;
            }
            if (!"switch".equals(type)
                    && (index.containsKey("withItems") || index.containsKey("parallelWithItems"))) {
                error("V25_DEPRECATED_LOOP", "Use the 'loop' option instead of withItems/parallelWithItems",
                        stepNode.range(), path, "loop");
            }
            if (!"switch".equals(type)) {
                var allowed = new LinkedHashSet<>(OPTIONS.get(type));
                allowed.add(type);
                unknown(index, allowed, path, true);
            }

            var branches = new LinkedHashMap<String, List<Step25>>();
            var options = new LinkedHashMap<String, Object>();
            var optionRanges = new LinkedHashMap<String, SourceRange>();
            var selector = index.get(type);
            var value = selector.value().value();
            if (Set.of("try", "block", "parallel").contains(type)) {
                var children = steps(selector.value(), path + "." + type);
                if (children.isEmpty()) {
                    error("V25_EMPTY_BRANCH", type + " requires a non-empty step list", selector.value().range(),
                            path + "." + type, null);
                }
                branches.put("body", children);
                value = null;
            } else if ("if".equals(type)) {
                var thenField = index.get("then");
                if (thenField == null) {
                    error("V25_REQUIRED", "if requires 'then'", stepNode.range(), path, "then");
                } else {
                    branches.put("then", steps(thenField.value(), path + ".then"));
                }
                var elseField = index.get("else");
                if (elseField != null) {
                    branches.put("else", steps(elseField.value(), path + ".else"));
                }
            } else if ("switch".equals(type)) {
                index.forEach((name, field) -> {
                    if (!"switch".equals(name)) {
                        branches.put(name, steps(field.value(), path + "." + name));
                    }
                });
            }
            if (!"switch".equals(type)) {
                OPTIONS.get(type).forEach(name -> {
                    var field = index.get(name);
                    if (field == null || Set.of("then", "else", "error").contains(name)) {
                        return;
                    }
                    var option = field.value().value();
                    if ("loop".equals(name) && option instanceof Map<?, ?> loop) {
                        option = normalizedLoop(loop);
                    } else if ("retry".equals(name) && option instanceof Map<?, ?> retry) {
                        option = normalizedRetry(retry);
                    }
                    options.put(name, option);
                    optionRanges.put(name, field.value().range());
                });
            }
            var error = index.get("error");
            if (error != null) {
                var handler = steps(error.value(), path + ".error");
                if (handler.isEmpty()) {
                    error("V25_EMPTY_ERROR", "error requires a non-empty step list", error.value().range(),
                            path + ".error", null);
                }
                branches.put("error", handler);
            }
            validateStep(type, selector, index, options, path);
            return new Step25(type, value, options, branches, stepNode.range(), path, selector.value().range(),
                    optionRanges);
        }

        private String selector(Map<String, Field> index, MappingNode node, String path) {
            if (index.containsKey("switch")) {
                return "switch";
            }
            var selectors = index.keySet().stream().filter(SELECTORS::contains).toList();
            if (selectors.size() != 1) {
                error("V25_STEP_SELECTOR", "A step must contain exactly one selector; found " + selectors.size(),
                        node.range(), path, null);
                return null;
            }
            return selectors.get(0);
        }

        private void validateStep(String type, Field selector, Map<String, Field> index,
                                  Map<String, Object> options, String path) {
            if ("switch".equals(type)) {
                requireString(selector, path + ".switch");
                return;
            }
            if (Set.of("task", "script", "call", "form", "checkpoint", "suspend").contains(type)) {
                requireString(selector, path + "." + type);
                if (selector.value() instanceof ScalarNode scalar
                        && scalar.value() instanceof String value && value.isBlank()) {
                    error("V25_REQUIRED", type + " requires a non-empty value", scalar.range(), path + "." + type, null);
                }
            }
            if ("checkpoint".equals(type) && selector.value() instanceof ScalarNode scalar
                    && "suspend".equals(scalar.value())) {
                error("V25_RESERVED_CHECKPOINT", "Checkpoint name 'suspend' is reserved for process suspension",
                        scalar.range(), path + ".checkpoint", null);
            }
            if ("form".equals(type) && selector.value() instanceof ScalarNode scalar
                    && scalar.value() instanceof String value && !value.startsWith("${")
                    && !value.matches("^[A-Za-z0-9_ $]+$")) {
                error("V25_FORM_NAME", "Form name must match ^[A-Za-z0-9_ $]+$", scalar.range(),
                        path + ".form", null);
            }
            if (Set.of("return", "exit").contains(type)
                    && (!(selector.value() instanceof ScalarNode scalar) || scalar.value() != null)) {
                error("V25_CONTROL_PAYLOAD", type + " does not accept a payload", selector.value().range(),
                        path + "." + type, null);
            }
            if ("set".equals(type)) {
                mapping(selector.value(), path + ".set", "set requires a mapping");
            }
            validateRetry(index.get("retry"), path + ".retry");
            validateLoop(index.get("loop"), path + ".loop");
            var meta = index.get("meta");
            if (meta != null) {
                mapping(meta.value(), path + ".meta", "meta must be a mapping");
            }
            var input = options.get("in");
            if (input != null && !(input instanceof Map<?, ?>) && !(input instanceof String)) {
                error("V25_INPUT", "in must be a mapping or expression", index.get("in").value().range(),
                        path + ".in", null);
            }
            validateOut(index.get("out"), path + ".out");
            requireBoolean(index.get("ignoreErrors"), path + ".ignoreErrors");
            requireBoolean(index.get("yield"), path + ".yield");
            requireBoolean(index.get("saveSubmittedBy"), path + ".saveSubmittedBy");
            requireString(index.get("body"), path + ".body");
        }

        private void validateRetry(Field field, String path) {
            if (field == null) {
                return;
            }
            if (field.value() instanceof ScalarNode scalar) {
                if (!isRetryValue(scalar.value())) {
                    error("V25_RETRY", "retry must be a non-negative whole number, expression, or mapping",
                            scalar.range(), path, null);
                }
                return;
            }
            var node = mapping(field.value(), path, "retry must be a number, expression, or mapping");
            var index = index(node, path, Set.of("times", "delay", "in"));
            unknown(index, Set.of("times", "delay", "in"), path, false);
            var times = index.get("times");
            if (times != null && (!(times.value() instanceof ScalarNode scalar) || !isRetryValue(scalar.value()))) {
                error("V25_RETRY", "retry.times must be a non-negative whole number or expression",
                        times.value().range(), path + ".times", null);
            }
            var delay = index.get("delay");
            if (delay != null && (!(delay.value() instanceof ScalarNode scalar) || !isRetryValue(scalar.value()))) {
                error("V25_RETRY", "retry.delay must be a non-negative whole number of seconds or expression",
                        delay.value().range(), path + ".delay", null);
            }
            requireMapping(index.containsKey("in") ? index.get("in").value().value() : null, index.get("in"),
                    path + ".in");
        }

        private void validateLoop(Field field, String path) {
            if (field == null) {
                return;
            }
            var node = mapping(field.value(), path, "loop must be a mapping");
            var index = index(node, path, Set.of("items", "mode", "parallelism"));
            unknown(index, Set.of("items", "mode", "parallelism"), path, false);
            if (index.get("items") == null) {
                error("V25_REQUIRED", "loop requires 'items'", node.range(), path, "items");
            } else {
                requireNonNull(index.get("items"), path + ".items");
            }
            var mode = index.get("mode");
            if (mode != null && (!(mode.value().value() instanceof String value)
                    || !Set.of("serial", "parallel").contains(value.toLowerCase()))) {
                error("V25_LOOP_MODE", "loop.mode must be serial or parallel", mode.value().range(), path + ".mode", null);
            }
            var parallelism = index.get("parallelism");
            if (parallelism != null && (!(parallelism.value() instanceof ScalarNode scalar)
                    || !isPositiveWholeNumberOrExpression(scalar.value()))) {
                error("V25_LOOP_PARALLELISM",
                        "loop.parallelism must be a positive whole number or expression",
                        parallelism.value().range(), path + ".parallelism", null);
            }
        }

        private Map<String, Object> normalizedLoop(Map<?, ?> loop) {
            var result = new LinkedHashMap<String, Object>();
            loop.forEach((key, value) -> result.put(String.valueOf(key), value));
            if (result.get("mode") instanceof String mode) {
                result.put("mode", mode.toLowerCase());
            }
            return result;
        }

        private Map<String, Object> normalizedRetry(Map<?, ?> retry) {
            var result = new LinkedHashMap<String, Object>();
            retry.forEach((key, value) -> result.put(String.valueOf(key), value));
            result.putIfAbsent("times", 1);
            return result;
        }

        private boolean isRetryValue(Object value) {
            if (value instanceof String text) {
                return text.startsWith("${") && text.endsWith("}");
            }
            if (!(value instanceof Number number)) {
                return false;
            }
            if (number instanceof Byte || number instanceof Short
                    || number instanceof Integer || number instanceof Long) {
                return number.longValue() >= 0;
            }
            return number instanceof java.math.BigInteger integer && integer.signum() >= 0;
        }
        private boolean isPositiveInt(Node node) {
            var value = resolve(node);
            if (!(value instanceof ScalarNode scalar) || !(scalar.value() instanceof Number number)) {
                return false;
            }
            try {
                return new java.math.BigDecimal(number.toString()).scale() <= 0
                        && new java.math.BigDecimal(number.toString()).compareTo(java.math.BigDecimal.ONE) >= 0
                        && new java.math.BigDecimal(number.toString()).compareTo(
                        java.math.BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private void validateEventInteger(Field field, String path, int minimum) {
            if (field == null) {
                return;
            }
            var node = resolve(field.value());
            if (!(node instanceof ScalarNode scalar) || !(scalar.value() instanceof Number number)) {
                error("V25_TYPE", path + " must be an integer", field.value().range(), path, null);
                return;
            }
            try {
                var value = new java.math.BigDecimal(number.toString());
                if (value.scale() > 0 || value.compareTo(java.math.BigDecimal.valueOf(minimum)) < 0) {
                    error("V25_TYPE", path + " must be an integer greater than or equal to " + minimum,
                            field.value().range(), path, null);
                }
            } catch (NumberFormatException e) {
                error("V25_TYPE", path + " must be an integer", field.value().range(), path, null);
            }
        }

        private void validateExclusiveMode(Field field, String path) {
            if (field != null && (!(resolve(field.value()) instanceof ScalarNode scalar)
                    || !(scalar.value() instanceof String value)
                    || !Set.of("cancel", "cancelold", "wait").contains(value.toLowerCase()))) {
                error("V25_TYPE", "exclusive.mode must be cancel, cancelOld, or wait",
                        field.value().range(), path, null);
            }
        }

        private void validateOut(Field field, String path) {
            if (field == null) {
                return;
            }
            var node = resolve(field.value());
            if (node instanceof ScalarNode scalar && scalar.value() instanceof String) {
                return;
            }
            if (node instanceof SequenceNode sequence) {
                for (var value : sequence.values()) {
                    if (!(resolve(value) instanceof ScalarNode scalar) || !(scalar.value() instanceof String)) {
                        error("V25_OUT", "out list values must be strings", value.range(), path, null);
                    }
                }
                return;
            }
            if (!(node instanceof MappingNode)) {
                error("V25_OUT", "out must be a string, list, or mapping", field.value().range(), path, null);
            }
        }

        private boolean isExpression(String value) {
            return value.startsWith("${") && value.endsWith("}");
        }
        private boolean isPositiveWholeNumberOrExpression(Object value) {
            if (value instanceof String text) {
                return text.startsWith("${") && text.endsWith("}");
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                return ((Number) value).longValue() >= 1;
            }
            return value instanceof java.math.BigInteger integer && integer.signum() > 0;
        }


        private void normalizeValidation(Map<String, Object> result, Field field, String path) {
            if (field == null) {
                return;
            }
            var node = mapping(field.value(), path, "validation must be a mapping");
            var index = index(node, path, Set.of("taskCalls"));
            unknown(index, Set.of("taskCalls"), path, false);
            var taskCalls = index.get("taskCalls");
            if (taskCalls == null) {
                return;
            }
            var taskCallsNode = mapping(taskCalls.value(), path + ".taskCalls", "taskCalls must be a mapping");
            var modes = index(taskCallsNode, path + ".taskCalls", Set.of("in", "out"));
            unknown(modes, Set.of("in", "out"), path + ".taskCalls", false);
            var normalizedModes = new LinkedHashMap<String, Object>();
            modes.forEach((name, value) -> {
                var mode = value.value().value();
                var normalized = mode != null ? mode.toString().toUpperCase() : null;
                if (!Set.of("DISABLED", "WARN", "FAIL").contains(normalized)) {
                    error("V25_VALIDATION_MODE", "Validation mode must be DISABLED, WARN, or FAIL",
                            value.value().range(), path + ".taskCalls." + name, null);
                } else {
                    normalizedModes.put(name, normalized);
                }
            });
            result.put("validation", Map.of("taskCalls", normalizedModes));
        }

        private void duplicates(Node node, String path) {
            duplicates(node, path, new IdentityHashMap<>(), 0);
        }

        private void duplicates(Node node, String path, IdentityHashMap<Node, Boolean> seen, int depth) {
            if (depth > 256 || seen.size() > 100_000) {
                error("V25_YAML_EXPANSION", "YAML anchor expansion exceeds the 100000-node or 256-level limit",
                        node.range(), path, null);
                return;
            }
            var expanded = resolve(node);
            if (seen.put(expanded, Boolean.TRUE) != null) {
                return;
            }
            if (expanded instanceof MappingNode mapping) {
                var names = new LinkedHashSet<String>();
                for (var field : mapping.fields()) {
                    if (!names.add(field.name())) {
                        error("V25_DUPLICATE_KEY", "Duplicate key '" + field.name() + "'", field.range(),
                                path + "." + field.name(), null);
                    }
                    duplicates(field.value(), path + "." + field.name(), seen, depth + 1);
                }
            } else if (expanded instanceof SequenceNode sequence) {
                for (var i = 0; i < sequence.values().size(); i++) {
                    duplicates(sequence.values().get(i), path + "[" + i + "]", seen, depth + 1);
                }
            }
        }

        private Map<String, Field> index(MappingNode node, String path, Set<String> allowed) {
            var result = new LinkedHashMap<String, Field>();
            for (var field : node.fields()) {
                result.putIfAbsent(field.name(), field);
            }
            return result;
        }

        private void unknown(Map<String, Field> index, Set<String> allowed, String path, boolean step) {
            index.forEach((name, field) -> {
                if (allowed.contains(name) || Set.of("withItems", "parallelWithItems").contains(name)) {
                    return;
                }
                var suggestion = nearest(name, allowed);
                error(step ? "V25_STEP_OPTION" : "V25_UNKNOWN_KEY", "Unknown key '" + name + "'",
                        field.range(), path + "." + name, suggestion);
            });
        }

        private MappingNode mapping(Node node, String path, String message) {
            var resolved = resolve(node);
            if (resolved instanceof MappingNode result) {
                return result;
            }
            error("V25_TYPE", message, node.range(), path, null);
            return new MappingNode(List.of(), node.range());
        }

        private SequenceNode sequence(Node node, String path, String message) {
            var resolved = resolve(node);
            if (resolved instanceof SequenceNode result) {
                return result;
            }
            error("V25_TYPE", message, node.range(), path, null);
            return new SequenceNode(List.of(), node.range());
        }

        private Set<String> stringSet(Field field, String path) {
            if (field == null) {
                return Set.of();
            }
            var node = sequence(field.value(), path, path + " must be a list");
            var result = new LinkedHashSet<String>();
            for (var value : node.values()) {
                if (value instanceof ScalarNode scalar && scalar.value() instanceof String text) {
                    result.add(text);
                } else {
                    error("V25_TYPE", path + " values must be strings", value.range(), path, null);
                }
            }
            return result;
        }

        private void requireMapping(Object value, Field field, String path) {
            if (field != null && !(value instanceof Map<?, ?>)) {
                error("V25_TYPE", path + " must be a mapping", field.value().range(), path, null);
            }
        }

        private void requireNonNull(Field field, String path) {
            if (field != null && resolve(field.value()) instanceof ScalarNode scalar && scalar.value() == null) {
                error("V25_STRUCTURAL_NULL", path + " must not be null", field.value().range(), path, null);
            }
        }

        private void requireString(Field field, String path) {
            if (field != null && (!(resolve(field.value()) instanceof ScalarNode scalar)
                    || !(scalar.value() instanceof String))) {
                error("V25_TYPE", path + " must be a string", field.value().range(), path, null);
            }
        }

        private void requireBoolean(Field field, String path) {
            if (field != null && (!(resolve(field.value()) instanceof ScalarNode scalar)
                    || !(scalar.value() instanceof Boolean))) {
                error("V25_TYPE", path + " must be a boolean", field.value().range(), path, null);
            }
        }

        private void requireStringList(Field field, String path) {
            if (field == null) {
                return;
            }
            var values = sequence(field.value(), path, path + " must be a list");
            for (var value : values.values()) {
                if (!(resolve(value) instanceof ScalarNode scalar) || !(scalar.value() instanceof String)) {
                    error("V25_TYPE", path + " values must be strings", value.range(), path, null);
                }
            }
        }

        private void validateImportSecret(Field field, String path) {
            if (field == null) {
                return;
            }
            var options = index(mapping(field.value(), path, "git secret must be a mapping"), path,
                    Set.of("name", "org", "password"));
            unknown(options, Set.of("name", "org", "password"), path, false);
            var name = options.get("name");
            if (name == null) {
                error("V25_IMPORT_REQUIRED", "git import secret requires a name", field.value().range(),
                        path + ".name", null);
            } else {
                requireString(name, path + ".name");
            }
            requireString(options.get("org"), path + ".org");
            requireString(options.get("password"), path + ".password");
        }

        private void addMavenImport(List<Import> result, Map<String, Object> options, SourceRange range, String path) {
            var url = options.get("url");
            if (!(url instanceof String text) || text.isBlank()) {
                error("V25_IMPORT_REQUIRED", "mvn import requires a non-empty url", range, path + ".mvn.url", null);
                return;
            }
            var builder = Import.MvnDefinition.builder().url(text);
            if (options.get("dest") != null) {
                builder.dest(normalizeContainedPath(options.get("dest").toString(), path + ".mvn.dest", range));
            }
            result.add(builder.build());
        }

        private void addGitImport(List<Import> result, Map<String, Object> options, SourceRange range, String path) {
            var builder = Import.GitDefinition.builder();
            putString(builder::name, options.get("name"));
            putString(builder::url, options.get("url"));
            putString(builder::version, options.get("version"));
            if (options.get("path") != null) {
                builder.path(normalizeContainedPath(options.get("path").toString(), path + ".git.path", range));
            }
            if (options.get("dest") != null) {
                builder.dest(normalizeContainedPath(options.get("dest").toString(), path + ".git.dest", range));
            }
            var excludes = strings(options.get("exclude"));
            if (!excludes.isEmpty()) {
                builder.exclude(excludes);
            }
            if (options.get("secret") instanceof Map<?, ?> secretMap) {
                @SuppressWarnings("unchecked") var secret = (Map<String, Object>) secretMap;
                var name = secret.get("name");
                if (name == null) {
                    error("V25_IMPORT_REQUIRED", "git import secret requires a name", range,
                            path + ".git.secret.name", null);
                } else {
                    var secretBuilder = Import.SecretDefinition.builder().name(name.toString());
                    putString(secretBuilder::org, secret.get("org"));
                    putString(secretBuilder::password, secret.get("password"));
                    builder.secret(secretBuilder.build());
                }
            }
            result.add(builder.build());
        }

        private void addDirectoryImport(List<Import> result, Map<String, Object> options, SourceRange range, String path) {
            var sourceValue = options.get("src");
            if (!(sourceValue instanceof String text) || text.isBlank()) {
                error("V25_IMPORT_REQUIRED", "dir import requires a non-empty src", range, path + ".dir.src", null);
                return;
            }
            var builder = Import.DirectoryDefinition.builder().src(text);
            if (options.get("dest") != null) {
                builder.dest(normalizeContainedPath(options.get("dest").toString(), path + ".dir.dest", range));
            }
            result.add(builder.build());
        }

        private String normalizeContainedPath(String value, String path, SourceRange range) {
            var relativeValue = value.startsWith("/") ? value.substring(1) : value;
            var normalized = Path.of(relativeValue).normalize();
            if (normalized.isAbsolute() || normalized.startsWith("..")) {
                error("V25_PATH_ESCAPE", "Import path escapes its project root", range, path, null);
            }
            return normalized.toString();
        }

        private void error(String code, String message, SourceRange range, String path, String suggestion) {
            diagnostics.add(new Diagnostic(code, Severity.ERROR, message, range, path, suggestion));
        }

        private void failIfErrors() {
            if (!diagnostics.isEmpty()) {
                throw new ModelException(diagnostics);
            }
        }

        private String nearest(String value, Set<String> allowed) {
            return allowed.stream()
                    .map(candidate -> Map.entry(candidate, distance(value, candidate)))
                    .filter(candidate -> candidate.getValue() <= Math.max(2, value.length() / 3))
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        private int distance(String first, String second) {
            var previous = new int[second.length() + 1];
            var current = new int[second.length() + 1];
            for (var j = 0; j <= second.length(); j++) {
                previous[j] = j;
            }
            for (var i = 1; i <= first.length(); i++) {
                current[0] = i;
                for (var j = 1; j <= second.length(); j++) {
                    var substitution = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                    current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                            previous[j - 1] + substitution);
                }
                var swap = previous;
                previous = current;
                current = swap;
            }
            return previous[second.length()];
        }

        private void putTriggerMap(java.util.function.Consumer<Map<String, Object>> consumer, Object value) {
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked") var typed = (Map<String, Object>) map;
                consumer.accept(typed);
            }
        }

        private void putSerializable(java.util.function.Consumer<Serializable> consumer, Object value) {
            if (value instanceof Serializable serializable) {
                consumer.accept(serializable);
            }
        }

        private void putString(java.util.function.Consumer<String> consumer, Object value) {
            if (value != null) {
                consumer.accept(value.toString());
            }
        }
    }

    private sealed interface Node permits MappingNode, SequenceNode, ScalarNode, AliasNode {

        SourceRange range();

        Object value();
    }

    private record Field(String name, SourceRange range, Node value) {
    }

    private static Node resolve(Node node) {
        while (node instanceof AliasNode alias) {
            node = alias.target();
        }
        return node;
    }

    private static final class MappingNode implements Node {

        private final List<Field> fields;
        private final SourceRange range;
        private volatile Map<String, Object> materialized;

        private MappingNode(List<Field> fields, SourceRange range) {
            this.fields = List.copyOf(fields);
            this.range = range;
        }

        private List<Field> fields() {
            return fields;
        }

        @Override
        public SourceRange range() {
            return range;
        }

        @Override
        public Map<String, Object> value() {
            var cached = materialized;
            if (cached == null) {
                var result = new LinkedHashMap<String, Object>();
                fields.forEach(field -> result.put(field.name(), field.value().value()));
                cached = Values.map(result);
                materialized = cached;
            }
            return cached;
        }
    }

    private static final class SequenceNode implements Node {

        private final List<Node> values;
        private final SourceRange range;
        private volatile List<Object> materialized;

        private SequenceNode(List<Node> values, SourceRange range) {
            this.values = List.copyOf(values);
            this.range = range;
        }

        private List<Node> values() {
            return values;
        }

        @Override
        public SourceRange range() {
            return range;
        }

        @Override
        public List<Object> value() {
            var cached = materialized;
            if (cached == null) {
                var result = new ArrayList<Object>(values.size());
                values.forEach(value -> result.add(value.value()));
                cached = Values.list(result);
                materialized = cached;
            }
            return cached;
        }
    }

    private record ScalarNode(Object value, SourceRange range) implements Node {
    }

    private record AliasNode(Node target, SourceRange range) implements Node {

        @Override
        public Object value() {
            return target.value();
        }
    }
}
