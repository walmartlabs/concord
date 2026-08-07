package com.walmartlabs.concord.runtime.v25.runner.plan;

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

import com.walmartlabs.concord.runtime.v25.model.ModelException;
import com.walmartlabs.concord.runtime.v25.model.parser.DefinitionParser;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanCompilerTest {

    @Test
    void producesStableIdentityAndInstructionIds() throws Exception {
        var source = """
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    value: 1
                flows:
                  default:
                    - expr: ${value + 1}
                      out: result
                    - call: child
                  child:
                    - set:
                        complete: true
                """;

        var first = compile(source);
        var second = compile(source);

        assertEquals(first.id(), second.id());
        assertEquals(0, first.flow("default").id());
        assertEquals(1, first.flow("child").id());
        assertEquals(0, first.flow("default").instructions().getFirst().id());
        assertEquals(1, first.flow("default").instructions().get(1).id());
        assertEquals(2, first.flow("child").instructions().getFirst().id());
    }

    @Test
    void distinguishesAmbiguousMapValuesAndDynamicFormDefinitions() throws Exception {
        var first = compile("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    a: "b;java.lang.String:c=java.lang.String:d"
                flows:
                  default:
                    - return
                """);
        var second = compile("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    a: b
                    c: d
                flows:
                  default:
                    - return
                """);
        var dynamicFirst = compile("""
                configuration:
                  runtime: concord-v2.5
                forms:
                  approval: ${firstFields}
                flows:
                  default:
                    - return
                """);
        var dynamicSecond = compile("""
                configuration:
                  runtime: concord-v2.5
                forms:
                  approval: ${secondFields}
                flows:
                  default:
                    - return
                """);

        assertNotEquals(first.id(), second.id());
        assertNotEquals(dynamicFirst.id(), dynamicSecond.id());
        assertEquals(first.id(), compile("""
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    a: "b;java.lang.String:c=java.lang.String:d"
                flows:
                  default:
                    - return
                """).id());
    }


    @Test
    void preservesRawScriptBodiesWithoutCompilingThemAsConcordEl() throws Exception {
        var first = compile("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - script: groovy
                      body: ${items.collect{ it }}
                """);
        var second = compile("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - script: groovy
                      body: ${items.collect{ it * 2 }}
                """);

        assertNotEquals(first.id(), second.id());
    }

    @Test
    void reportsInvalidFormLabelsAtTheirLabelPath() throws Exception {
        var error = assertThrows(ModelException.class, () -> validate("""
                configuration:
                  runtime: concord-v2.5
                forms:
                  approval:
                    - comment:
                        type: string
                        label: ${value + }
                flows:
                  default:
                    - return
                """));

        assertEquals("$.forms.approval.comment.label", error.diagnostics().getFirst().path());
    }
    @Test
    void rejectsUnknownStaticFlowBeforeExecution() throws Exception {
        var error = assertThrows(IllegalArgumentException.class, () -> compile("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - call: missing
                """));

        assertTrue(error.getMessage().contains("Unknown flow 'missing'"));
        assertTrue(error.getMessage().contains("flows.default[0]"));
    }

    @Test
    void validatesExpressionsInProfilesAndForms() throws Exception {
        var formError = assertThrows(ModelException.class, () -> validate("""
                configuration:
                  runtime: concord-v2.5
                forms:
                  approval: ${value + }
                flows:
                  default:
                    - return
                """));

        assertEquals("$.forms.approval", formError.diagnostics().getFirst().path());

        var profileError = assertThrows(ModelException.class, () -> validate("""
                configuration:
                  runtime: concord-v2.5
                profiles:
                  production:
                    flows:
                      default:
                        - expr: ${value + }
                flows:
                  default:
                    - return
                """));

        assertEquals("profiles.production.flows.default[0].expr",
                profileError.diagnostics().getFirst().path());
    }

    private void validate(String source) throws Exception {
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream(
                source.getBytes(StandardCharsets.UTF_8)));
        new PlanCompiler(new ExpressionService()).validate(definition);
    }

    private ExecutionPlan compile(String source) throws Exception {
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream(
                source.getBytes(StandardCharsets.UTF_8)));
        return new PlanCompiler(new ExpressionService()).compile(definition);
    }
}
