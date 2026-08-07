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

import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.runtime.model.Options;
import com.walmartlabs.concord.runtime.v25.model.Definition25;
import com.walmartlabs.concord.runtime.v25.model.Form25;
import com.walmartlabs.concord.runtime.v25.model.ModelException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionParserTest {

    private final DefinitionParser parser = new DefinitionParser();

    @Test
    void taskOptionOrderDoesNotChangeTheStep() throws Exception {
        var inputs = List.of(
                "task: test\nin: {a: 1}\nout: result\nmeta: {name: sample}",
                "in: {a: 1}\nmeta: {name: sample}\ntask: test\nout: result",
                "out: result\ntask: test\nmeta: {name: sample}\nin: {a: 1}",
                "meta: {name: sample}\nout: result\nin: {a: 1}\ntask: test");

        var normalized = inputs.stream().map(this::parseStep).map(step ->
                List.of(step.type(), step.value(), step.options())).toList();

        assertEquals(1, normalized.stream().distinct().count());
    }

    @Test
    void preservesLiteralNullInData() throws Exception {
        var definition = parse("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    optional: null
                flows:
                  default:
                    - task: test
                      in:
                        optional: null
                """);

        assertTrue(definition.configuration().arguments().containsKey("optional"));
        assertEquals(null, definition.configuration().arguments().get("optional"));
        @SuppressWarnings("unchecked") var input = (Map<String, Object>) definition.flows().get("default")
                .steps().get(0).options().get("in");
        assertTrue(input.containsKey("optional"));
        assertEquals(null, input.get("optional"));
    }

    @Test
    void reportsDuplicateKeyWithLocationAndPath() {
        var error = assertThrows(ModelException.class, () -> parse("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - task: first
                      task: second
                """));

        assertTrue(error.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("V25_DUPLICATE_KEY")
                        && diagnostic.range().line() == 6
                        && diagnostic.path().equals("$.flows.default[0].task")), error.diagnostics().toString());
    }

    @Test
    void rejectsDeprecatedLoopAliases() {
        var error = assertThrows(ModelException.class, () -> parse("""
                flows:
                  default:
                    - task: test
                      withItems: [a, b]
                """));

        assertTrue(error.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("V25_DEPRECATED_LOOP") && "loop".equals(diagnostic.suggestion())));
    }

    @Test
    void normalizesValidationModesAndFormReadOnly() throws Exception {
        var definition = parse("""
                configuration:
                  validation:
                    taskCalls:
                      in: warn
                      out: FaIl
                forms:
                  approval:
                    - startsAt:
                        type: date?
                        allow: ["2026-08-06"]
                        readOnly: true
                    - endsAt:
                        type: dateTime
                        readOnly: true
                """);

        @SuppressWarnings("unchecked") var validation = (Map<String, Object>) definition.configuration()
                .values().get("validation");
        @SuppressWarnings("unchecked") var taskCalls = (Map<String, Object>) validation.get("taskCalls");
        assertEquals(Map.of("in", "WARN", "out", "FAIL"), taskCalls);
        assertTrue((Boolean) definition.formDefinitions().get("approval").fields().get(0).options().get("readOnly"));
        assertTrue((Boolean) definition.formDefinitions().get("approval").fields().get(1).options().get("readOnly"));
        assertEquals("date?", definition.formDefinitions().get("approval").fields().get(0).type());
        assertEquals(List.of("2026-08-06"),
                definition.formDefinitions().get("approval").fields().get(0).allowedValue());
    }

    @Test
    void preservesV2TriggerVersionsInConditions() throws Exception {
        var definition = parse("""
                triggers:
                  - github: {version: 2, conditions: {type: push}, entryPoint: default}
                  - oneops: {version: 2, conditions: {state: active}, entryPoint: default}
                """);

        for (var trigger : definition.triggers()) {
            assertEquals(2, trigger.conditions().get("version"));
            assertFalse(trigger.configuration().containsKey("version"));
            assertFalse(trigger.configuration().containsKey("conditions"));
        }
    }

    @Test
    void supportsV2RetryMapsAndRejectsInvalidExecutionValues() throws Exception {
        var definition = parse("""
                flows:
                  default:
                    - task: test
                      retry: {delay: 2}
                """);
        @SuppressWarnings("unchecked") var retry = (Map<String, Object>) definition.flows().get("default").steps()
                .get(0).options().get("retry");
        assertEquals(Map.of("times", 1, "delay", 2), retry);

        for (var retryValue : List.of("{delay: PT1S}", "{delay: 1.5}", "{times: -1}")) {
            assertThrows(ModelException.class, () -> parse("""
                    flows:
                      default:
                        - task: test
                          retry: %s
                    """.formatted(retryValue)));
        }
    }

    @Test
    void supportsV2StructuredLogFormAndSwitchShapes() throws Exception {
        var definition = parse("""
                flows:
                  default:
                    - log: {event: started}
                    - logYaml: [{event: finished}]
                    - form: approval
                      fields: "${formFields}"
                      values: "${formValues}"
                      runAs: {username: concord}
                    - form: approval
                      runAs: "${formRunAs}"
                    - switch: "${result}"
                      meta: [{log: matched-meta}]
                """);

        var steps = definition.flows().get("default").steps();
        assertEquals(Map.of("event", "started"), steps.get(0).value());
        assertEquals(List.of(Map.of("event", "finished")), steps.get(1).value());
        assertEquals("${formFields}", steps.get(2).options().get("fields"));
        assertEquals("${formValues}", steps.get(2).options().get("values"));
        assertEquals(Map.of("username", "concord"), steps.get(2).options().get("runAs"));
        assertEquals("${formRunAs}", steps.get(3).options().get("runAs"));
        assertEquals(1, steps.get(4).branch("meta").size());
    }

    @Test
    void normalizesV2ImportPathsWithoutRestrictingDirectorySources() throws Exception {
        var definition = parse("""
                imports:
                  - mvn: {url: mvn:com.example:flow:1.0, dest: /archive}
                  - git: {url: https://example.com/flow.git, path: /flows, dest: /dependencies}
                  - dir: {src: /opt/shared-flows, dest: /dependencies}
                """);

        var mvn = (Import.MvnDefinition) definition.imports().items().get(0);
        var git = (Import.GitDefinition) definition.imports().items().get(1);
        var directory = (Import.DirectoryDefinition) definition.imports().items().get(2);
        assertEquals("archive", mvn.dest());
        assertEquals("flows", git.path());
        assertEquals("dependencies", git.dest());
        assertEquals("/opt/shared-flows", directory.src());
        assertEquals("dependencies", directory.dest());
        assertThrows(ModelException.class, () -> parse("""
                imports:
                  - dir: {src: /opt/shared-flows, dest: ../outside}
                """));
    }

    @Test
    void mergeUsesRootPrecedenceAndDeepMergesArguments() throws Exception {
        var extra = parse("""
                configuration:
                  arguments:
                    nested: {a: 1, same: extra}
                  dependencies: [one]
                flows:
                  default:
                    - log: extra
                """);
        var root = parse("""
                configuration:
                  entryPoint: root
                  arguments:
                    nested: {b: 2, same: root}
                  dependencies: [two, one]
                flows:
                  default:
                    - log: root
                """);

        var merged = parser.merge(List.of(extra, root));

        assertEquals("root", merged.configuration().entryPoint());
        assertEquals(List.of("one", "two"), merged.configuration().dependencies());
        assertEquals(Map.of("a", 1, "b", 2, "same", "root"), merged.configuration().arguments().get("nested"));
        assertEquals("root", merged.flows().get("default").steps().get(0).value());
    }

    @Test
    void rejectsEscapingPaths() {
        var root = Path.of("target/project").toAbsolutePath();
        assertThrows(IllegalArgumentException.class,
                () -> DefinitionParser.resolveContained(root, "../secret", "resources.concord"));
        assertTrue(DefinitionParser.resolveContained(root, "concord/flow.yml", "resources.concord")
                .startsWith(root));
    }

    @Test
    void unknownOptionsIncludeNearestSuggestion() {
        var error = assertThrows(ModelException.class, () -> parse("""
                flows:
                  default:
                    - out: result
                      task: test
                      retri: 2
                """));

        assertTrue(error.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("V25_STEP_OPTION") && "retry".equals(diagnostic.suggestion())));
        assertFalse(error.getMessage().contains("password"));
    }

    @Test
    void parsesEverySupportedStepSelector() throws Exception {
        var definition = parse("""
                flows:
                  child:
                    - return
                  default:
                    - log: hello
                    - logYaml: {a: 1}
                    - task: test
                      loop: {items: [a], mode: serial}
                    - script: groovy
                      body: "result = 1"
                    - expr: "${1 + 1}"
                      out: two
                    - call: child
                    - set: {value: 1}
                    - if: "${enabled}"
                      then:
                        - log: enabled
                      else:
                        - log: disabled
                    - switch: "${environment}"
                      prod:
                        - log: production
                      default:
                        - log: other
                    - try:
                        - throw: retry
                      error:
                        - log: handled
                    - block:
                        - log: grouped
                    - parallel:
                        - log: first
                        - log: second
                      out: [first, second]
                    - form: approval
                      fields: "${fields}"
                    - checkpoint: beforeDeploy
                    - suspend: "${eventName}"
                    - throw: bad-input
                    - return
                    - exit
                    - "${notify()}"
                """);

        assertEquals(List.of("log", "logYaml", "task", "script", "expr", "call", "set", "if", "switch",
                        "try", "block", "parallel", "form", "checkpoint", "suspend", "throw", "return", "exit",
                        "expr"),
                definition.flows().get("default").steps().stream().map(step -> step.type()).toList());
        assertEquals(2, definition.flows().get("default").steps().get(11).branch("body").size());
        assertEquals(1, definition.flows().get("default").steps().get(8).branch("prod").size());
    }

    @Test
    void parsesTopLevelDefinitionsAndAppliesProfilesInOrder() throws Exception {
        var definition = parse("""
                configuration:
                  runtime: concord-v2.5
                  activeProfiles: [blue]
                  arguments:
                    nested: {base: true, winner: base}
                publicFlows: [default]
                flows:
                  default:
                    - log: base
                profiles:
                  blue:
                    configuration:
                      arguments:
                        nested: {profile: true, winner: blue}
                    flows:
                      default:
                        - log: blue
                    forms:
                      profileForm: "${profileFields}"
                triggers:
                  - manual:
                      entryPoint: default
                      arguments: {source: manual}
                forms:
                  approval:
                    - approved: boolean
                imports:
                  - mvn:
                      url: mvn://com.example:tasks:1.0
                      dest: lib
                resources:
                  concord: [concord/extra.concord.yml]
                """);

        var effective = definition.effective(List.of("blue", "flag-only"));
        @SuppressWarnings("unchecked") var nested =
                (Map<String, Object>) effective.configuration().arguments().get("nested");

        assertEquals(Map.of("base", true, "profile", true, "winner", "blue"), nested);
        assertEquals("blue", effective.flows().get("default").steps().get(0).value());
        assertTrue(effective.formDefinitions().containsKey("profileForm"));
        assertEquals(Set.of("default"), definition.publicFlows());
        assertEquals("manual", definition.triggers().get(0).name());
        assertEquals(1, definition.imports().items().size());
        assertEquals(List.of("concord/extra.concord.yml"), definition.resources().get("concord"));
    }

    @Test
    void serializesAnEffectiveDefinitionWithRuntimeOptionsAndDynamicForms() throws Exception {
        var definition = parse("""
                configuration:
                  arguments: {fromDefinition: ignored}
                flows:
                  default:
                    - log: base
                profiles:
                  blue:
                    flows:
                      default:
                        - log: blue
                    forms:
                      dynamic: "${fields}"
                """);
        var instanceId = UUID.randomUUID();
        var parentId = UUID.randomUUID();
        var output = new ByteArrayOutputStream();
        Options options = new Options() {
            @Override
            public UUID instanceId() {
                return instanceId;
            }

            @Override
            public UUID parentInstanceId() {
                return parentId;
            }

            @Override
            public String entryPoint() {
                return "selected";
            }

            @Override
            public Map<String, Object> configuration() {
                return Map.of("arguments", Map.of("fromOptions", true), "initiator", "alice",
                        "projectInfo", Map.of("name", "project"), "processInfo", Map.of("name", "process"));
            }

            @Override
            public List<String> activeProfiles() {
                return List.of("blue");
            }
        };
        definition.serialize(options, output);

        var roundTripped = parser.parse("effective.concord.yml", new ByteArrayInputStream(output.toByteArray()));
        assertEquals("blue", roundTripped.flows().get("default").steps().get(0).value());
        assertEquals("selected", roundTripped.configuration().entryPoint());
        assertEquals(true, roundTripped.configuration().arguments().get("fromOptions"));
        assertFalse(roundTripped.configuration().arguments().containsKey("fromDefinition"));
        assertEquals(instanceId.toString(), roundTripped.configuration().arguments().get("txId"));
        assertEquals(parentId.toString(), roundTripped.configuration().arguments().get("parentInstanceId"));
        assertEquals("alice", roundTripped.configuration().arguments().get("initiator"));
        var form = (Form25) roundTripped.formDefinitions().get("dynamic");
        assertEquals("${fields}", form.fieldsExpression());
    }

    @Test
    void mapsCronSpecAndTimezoneToTriggerConditions() throws Exception {
        var definition = parse("""
                triggers:
                  - cron:
                      spec: "0 0 * * *"
                      timezone: UTC
                      entryPoint: scheduled
                      runAs: {withSecret: deployer}
                      arguments: {dryRun: true}
                      activeProfiles: [blue]
                      exclusive: {group: scheduled, mode: cancel}
                """);

        var trigger = definition.triggers().get(0);
        assertEquals("cron", trigger.name());
        assertEquals(Map.of("spec", "0 0 * * *", "timezone", "UTC"), trigger.conditions());
        assertEquals(Map.of("entryPoint", "scheduled", "runAs", Map.of("withSecret", "deployer"),
                "exclusive", Map.of("group", "scheduled", "mode", "cancel")), trigger.configuration());
        assertEquals(Map.of("dryRun", true), trigger.arguments());
        assertEquals(List.of("blue"), trigger.activeProfiles());
    }

    @Test
    void preservesSwitchLabelsAndNormalizesLoopModes() throws Exception {
        var definition = parse("""
                flows:
                  default:
                    - switch: "${kind}"
                      meta:
                        - log: metadata
                      withItems:
                        - log: supported-label
                    - task: test
                      loop: {items: [a], mode: PARALLEL}
                """);

        var switchStep = definition.flows().get("default").steps().get(0);
        assertEquals(List.of("meta", "withItems"), switchStep.branches().keySet().stream().toList());
        assertEquals("parallel", ((Map<?, ?>) definition.flows().get("default").steps().get(1)
                .options().get("loop")).get("mode"));
    }

    @Test
    void reportsDuplicateNestedDataKeys() {
        var error = assertThrows(ModelException.class, () -> parse("""
                configuration:
                  arguments: {key: first, key: second}
                """));

        assertTrue(error.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("V25_DUPLICATE_KEY") && diagnostic.path().equals("$.configuration.arguments.key")));
    }

    @Test
    void resolvesYamlAnchorsInArgumentsAndStepInputs() throws Exception {
        var definition = parse("""
                configuration:
                  arguments:
                    shared: &shared
                      values: [one, two]
                    copy: *shared
                flows:
                  default:
                    - task: example
                      in: *shared
                """);

        var shared = definition.configuration().arguments().get("shared");
        assertEquals(shared, definition.configuration().arguments().get("copy"));
        assertEquals(shared, definition.flows().get("default").steps().get(0).options().get("in"));
    }

    @Test
    void rejectsUndefinedYamlAliases() {
        var error = assertThrows(ModelException.class, () -> parse("""
                configuration:
                  arguments:
                    copy: *missing
                """));

        assertTrue(error.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("V25_YAML_ALIAS") && diagnostic.message().contains("missing")));
    }

    @Test
    void reportsTypeErrorsAtYamlAliasUseSites() {
        var error = assertThrows(ModelException.class, () -> parse("""
                configuration:
                  arguments:
                    number: &number {value: 42}
                  dependencies: *number
                """));

        assertTrue(error.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.path().equals("configuration.dependencies") && diagnostic.range().line() == 4),
                error.diagnostics().toString());
    }

    @Test
    void rejectsReservedCheckpointName() {
        var error = assertThrows(ModelException.class, () -> parse("""
                flows:
                  default:
                    - checkpoint: suspend
                """));

        assertTrue(error.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("V25_RESERVED_CHECKPOINT")
                        && diagnostic.message().contains("reserved")));
    }

    @Test
    void rejectsRuntimeOnlyShapesWithLocatedDiagnostics() {
        var cases = List.of(
                "flows: {default: []}",
                "forms: {approval: plain-text}",
                "flows: {default: [{task: test, out: false}]}",
                "configuration: {exclusive: {mode: cancel}}",
                "configuration: {events: {batchSize: -1}}",
                "configuration: {parallelLoopParallelism: 1.5}",
                "imports: [{git: {url: x, exclude: README.md}}]");

        for (var definition : cases) {
            var error = assertThrows(ModelException.class, () -> parse(definition));
            assertTrue(error.diagnostics().stream().allMatch(diagnostic -> diagnostic.range() != null),
                    error.diagnostics().toString());
        }
    }

    @Test
    void handlesRepeatedYamlAnchorsWithoutExponentialExpansion() throws Exception {
        var document = new StringBuilder("configuration:\n  arguments:\n    a0: &a0 [value]\n");
        for (var i = 1; i <= 20; i++) {
            document.append("    a").append(i).append(": &a").append(i)
                    .append(" [*a").append(i - 1).append(", *a").append(i - 1).append("]\n");
        }

        assertTrue(parse(document.toString()).configuration().arguments().containsKey("a20"));
    }

    @Test
    void validatesTriggerOptionsAtParseTime() {
        var github = assertThrows(ModelException.class, () -> parse("""
                triggers:
                  - github: {version: 2, entryPoint: default}
                """));
        assertTrue(github.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.path().endsWith(".github") && "conditions".equals(diagnostic.suggestion())));

        var version = assertThrows(ModelException.class, () -> parse("""
                triggers:
                  - github: {version: 2.5, conditions: {type: push}, entryPoint: default}
                """));
        assertTrue(version.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("V25_TRIGGER_VERSION")));

        var cron = assertThrows(ModelException.class, () -> parse("""
                triggers:
                  - cron: {spec: "* * * * *", timezome: UTC, entryPoint: default}
                """));
        assertTrue(cron.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("V25_UNKNOWN_KEY") && diagnostic.path().endsWith(".timezome")));
    }

    @Test
    void validatesFormResourcesAndLoopOptionsAtParseTime() throws Exception {
        var yield = assertThrows(ModelException.class, () -> parse("""
                flows:
                  default:
                    - form: approval
                      yield: "true"
                """));
        assertTrue(yield.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.path().endsWith(".yield") && diagnostic.code().equals("V25_TYPE")));

        var formName = assertThrows(ModelException.class, () -> parse("""
                flows:
                  default:
                    - form: invalid.name
                """));
        assertTrue(formName.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("V25_FORM_NAME")));

        var resources = assertThrows(ModelException.class, () -> parse("""
                resources:
                  concord: [null]
                """));
        assertTrue(resources.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.path().equals("resources.concord") && diagnostic.code().equals("V25_TYPE")));

        var scalarResources = assertThrows(ModelException.class, () -> parse("""
                resources:
                  concord: concord.yml
                """));
        assertTrue(scalarResources.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.path().equals("resources.concord") && diagnostic.code().equals("V25_TYPE")));

        for (var parallelism : List.of("0", "1.5", "nope")) {
            var error = assertThrows(ModelException.class, () -> parse("""
                    flows:
                      default:
                        - task: example
                          loop: {items: [one], mode: parallel, parallelism: %s}
                    """.formatted(parallelism)));
            assertTrue(error.diagnostics().stream().anyMatch(diagnostic ->
                    diagnostic.code().equals("V25_LOOP_PARALLELISM")));
        }

        var expression = parse("""
                flows:
                  default:
                    - task: example
                      loop: {items: [one], mode: parallel, parallelism: "${workers}"}
                """);
        assertEquals("${workers}", ((Map<?, ?>) expression.flows().get("default").steps().get(0)
                .options().get("loop")).get("parallelism"));
    }

    private com.walmartlabs.concord.runtime.v25.model.Step25 parseStep(String step) {
        try {
            return parse("flows:\n  default:\n    - " + step.replace("\n", "\n      ") + "\n")
                    .flows().get("default").steps().get(0);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Definition25 parse(String value) throws Exception {
        return parser.parse("concord.yml", new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }
}
