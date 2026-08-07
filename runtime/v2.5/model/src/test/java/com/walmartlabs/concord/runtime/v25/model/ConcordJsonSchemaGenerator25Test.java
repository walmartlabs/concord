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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcordJsonSchemaGenerator25Test {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final com.networknt.schema.JsonSchema schema = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(new ObjectMapper().valueToTree(ConcordJsonSchemaGenerator25.schema()));

    @Test
    void declaresTheRuntimeAndAllStepSelectors() throws Exception {
        var generated = ConcordJsonSchemaGenerator25.schema();
        @SuppressWarnings("unchecked") var properties = (Map<String, Object>) generated.get("properties");
        @SuppressWarnings("unchecked") var configuration = (Map<String, Object>) ((Map<String, Object>) generated
                .get("$defs")).get("configuration");
        @SuppressWarnings("unchecked") var configurationProperties =
                (Map<String, Object>) configuration.get("properties");
        @SuppressWarnings("unchecked") var runtime = (Map<String, Object>) configurationProperties.get("runtime");
        @SuppressWarnings("unchecked") var definitions = (Map<String, Object>) generated.get("$defs");
        @SuppressWarnings("unchecked") var step = (Map<String, Object>) definitions.get("step");
        @SuppressWarnings("unchecked") var alternatives = (List<Map<String, Object>>) step.get("oneOf");

        assertEquals("concord-v2.5", runtime.get("const"));
        assertEquals(20, alternatives.size());
        assertTrue(definitions.keySet().containsAll(List.of(
                "configuration", "profile", "trigger", "import", "resources", "formField", "retry", "loop")));
        var json = new ObjectMapper();
        assertEquals(json.writeValueAsString(ConcordJsonSchemaGenerator25.schema()),
                json.writeValueAsString(ConcordJsonSchemaGenerator25.schema()));
    }

    @Test
    void validatesACompleteV25Definition() throws Exception {
        assertValid("""
                configuration:
                  runtime: concord-v2.5
                  entryPoint: default
                  arguments: {greeting: hello}
                  dependencies: [mvn:com.example:library:1.0]
                  extraDependencies: [mvn:com.example:extra:1.0]
                  debug: false
                  activeProfiles: [test]
                  meta: {owner: runtime}
                  events: {recordEvents: true, batchSize: 10, batchFlushInterval: 1000, updateMetaOnAllEvents: true}
                  requirements: {agent: linux}
                  processTimeout: PT1H
                  suspendTimeout: PT5M
                  exclusive: {group: deployments, mode: wait}
                  out: [result]
                  template: example
                  parallelLoopParallelism: 2
                  validation: {taskCalls: {in: WARN, out: FAIL}}
                flows:
                  default:
                    - {log: starting, meta: {level: info}, name: start}
                    - {logYaml: {a: 1}}
                    - task: exampleTask
                      in: {message: hello}
                      out: result
                      retry: {times: 2, delay: 1, in: {reason: retry}}
                      loop: {items: [one, two], mode: PARALLEL, parallelism: 2}
                      ignoreErrors: false
                      error: [{log: recovered}]
                    - {script: groovy, body: 'result = 1', in: {value: 1}, out: scriptResult}
                    - {expr: '${result}', out: expressionResult}
                    - {call: child, in: {message: hello}, out: childResult}
                    - {set: {answer: 42}, meta: {source: example}}
                    - if: '${enabled}'
                      then: [{log: enabled}]
                      else: [{log: disabled}]
                    - switch: '${result}'
                      success: [{log: success}]
                      default: [{log: fallback}]
                      meta: [{log: meta-label}]
                    - try: [{log: attempt}]
                      error: [{log: failed}]
                    - block: [{log: block}]
                    - parallel: [{log: parallel}]
                      out: [parallelResult]
                    - form: approval
                      fields:
                        - name:
                            type: string
                            label: Name
                            default: anonymous
                            allow: [alice, bob]
                      values: {name: alice}
                      runAs: {withSecret: form-user}
                      yield: true
                      saveSubmittedBy: true
                    - {checkpoint: saved, meta: {checkpoint: one}}
                    - {suspend: approval-needed, meta: {reason: review}}
                    - {throw: failed, name: failure}
                    - return:
                    - exit:
                  child: [{log: child}]
                publicFlows: [default]
                profiles:
                  test:
                    configuration: {arguments: {test: true}}
                    flows: {profileFlow: [{log: profile}]}
                    forms: {profileForm: [{value: string}]}
                triggers:
                  - manual: {name: manual, entryPoint: default, arguments: {manual: true}}
                  - cron: {spec: '0 * * * *', timezone: UTC, entryPoint: default, runAs: {withSecret: cron-user}}
                  - github:
                      version: 2
                      conditions: {type: push, branch: main}
                      entryPoint: default
                  - oneops: {version: 2, conditions: {state: active}, entryPoint: default}
                  - webhook: {version: 2, conditions: {event: created}, entryPoint: default}
                forms:
                  approval:
                    - approved: boolean
                  dynamic: '${forms.dynamic}'
                imports:
                  - mvn: {url: 'mvn:com.example:flow:1.0', dest: dependencies}
                  - dir: {src: flows, dest: imported}
                  - git:
                      name: shared
                      url: https://example.com/shared.git
                      version: main
                      path: flows
                      dest: imported
                      secret: {name: git-token, org: concord}
                      exclude: [README.md]
                resources:
                  concord: [glob:concord/**/*.yml]
                """);
    }

    @Test
    void validatesV2CompatibleStepShapes() throws Exception {
        assertValid("""
                flows:
                  default:
                    - log: {event: started}
                    - logYaml: [{event: finished}]
                    - task: test
                      retry: {delay: 2}
                    - form: approval
                      fields: '${formFields}'
                      values: '${formValues}'
                      runAs: {username: concord}
                    - form: approval
                      runAs: '${formRunAs}'
                    - switch: '${result}'
                      meta: [{log: matched-meta}]
                triggers:
                  - github: {version: 2, conditions: {type: push}, entryPoint: default}
                  - oneops: {version: 2, conditions: {state: active}, entryPoint: default}
                """);
    }

    @Test
    void alignsWithParserForOptionalEventsCaseInsensitiveEnumsAndExpandedValues() throws Exception {
        assertValid("""
                configuration:
                  events: {recordEvents: true}
                  exclusive: {group: deployment, mode: CANCELold}
                  validation: {taskCalls: {in: warn}}
                flows:
                  default:
                    - throw: {message: failed, code: 42}
                    - task: test
                      loop: {items: [one], mode: PaRaLlEl, parallelism: 1}
                triggers:
                  - github:
                      version: 2
                      conditions:
                        type: push
                        files: {modified: [src/.*]}
                      entryPoint: default
                """);

        assertInvalid("flows: {default: []}");
        assertInvalid("flows: {default: [{checkpoint: suspend}]}");
        assertInvalid("flows: {default: [{form: invalid.name}]}");
    }

    @Test
    void rejectsMalformedSelectorsAndNestedStructures() throws Exception {
        for (var definition : List.of(
                "flows: {default: [{task: null}]}",
                "flows: {default: [{task: [not-a-name]}]}",
                "flows: {default: [{task: example, ignoreErrors: 'true'}]}",
                "flows: {default: [{script: groovy, body: 42}]}",
                "flows: {default: [{task: example, out: false}]}",
                "flows: {default: [{task: example, retry: {times: false}}]}",
                "flows: {default: [{task: example, retry: {delay: PT1S}}]}",
                "flows: {default: [{task: example, retry: {delay: 1.5}}]}",
                "flows: {default: [{task: example, loop: {items: [one], parallelism: nope}}]}",
                "configuration: {dependencies: not-a-list}",
                "configuration: {events: {unexpected: true}}",
                "triggers: [{cron: {entryPoint: default}}]",
                "triggers: [{manual: {entryPoint: default}, cron: {spec: '* * * * *', entryPoint: default}}]",
                "imports: [{mvn: {dest: dependencies}}]",
                "imports: [{mvn: {url: x, unexpected: true}}]",
                "imports: [{unsupported: {version: 2, entryPoint: default}}]",
                "forms: {approval: [{name: {type: 1}}]}",
                "forms: {approval: plain-text}",
                "resources: {other: [x]}")) {
            assertInvalid(definition);
        }
    }

    private void assertValid(String definition) throws Exception {
        var errors = schema.validate(yaml.readTree(definition));
        assertTrue(errors.isEmpty(), errors::toString);
    }

    private void assertInvalid(String definition) throws Exception {
        var errors = schema.validate(yaml.readTree(definition));
        assertFalse(errors.isEmpty(), () -> "Expected validation to fail: " + definition);
    }
}
