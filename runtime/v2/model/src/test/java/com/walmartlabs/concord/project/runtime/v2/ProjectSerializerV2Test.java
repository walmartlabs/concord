package com.walmartlabs.concord.project.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import com.walmartlabs.concord.project.runtime.v2.parser.AbstractParserTest;
import com.walmartlabs.concord.runtime.v2.ProjectSerializerV2;
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.parser.SimpleOptions;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectSerializerV2Test extends AbstractParserTest {

    private final ProjectSerializerV2 serializer = new ProjectSerializerV2();

    @Test
    public void testConfiguration() throws Exception {
        ProcessDefinitionConfiguration cfg = ProcessDefinitionConfiguration.builder()
                .processTimeout(Duration.parse("PT1H"))
                .build();

        String result = toYaml(cfg);
        assertTrue(result.contains("processTimeout: \"PT1H\""));
    }

    @Test
    public void testCheckpoint() throws Exception {
        String result = toYaml(new Checkpoint(location(), "checkpoint-1", simpleOptions()));
        assertEquals("checkpoint: \"checkpoint-1\"\n" +
                "meta:\n" +
                "  meta-1: \"v1\"\n" +
                "  meta-2: \"v2\"\n", result);
    }

    @Test
    public void testExit() throws Exception {
        String result = toYaml(new ExitStep(location()));
        assertEquals("\"exit\"\n", result);
    }

    @Test
    public void testExpression() throws Exception {
        ExpressionOptions opts = ExpressionOptions.builder()
                .out("out-result")
                .meta(meta())
                .errorSteps(steps())
                .build();

        String result = toYaml(new Expression(location(), "${a}", opts));
        assertResult("serializer/expressionStep.yml", result);
    }

    @Test
    public void testFlowCall() throws Exception {
        FlowCallOptions opts = FlowCallOptions.builder()
                .putInput("in-1", "v1")
                .addOut("o1")
                .withItems(withItems())
                .loop(serialLoop())
                .retry(retry())
                .errorSteps(steps())
                .build();

        String result = toYaml(new FlowCall(location(), "flow", opts));
        assertResult("serializer/flowCallStep.yml", result);
    }

    @Test
    public void testFlowCallOutExpr() throws Exception {
        FlowCallOptions opts = FlowCallOptions.builder()
                .putInput("in-1", "v1")
                .putOutExpr("o1", "${o.one}")
                .withItems(withItems())
                .retry(retry())
                .errorSteps(steps())
                .build();

        String result = toYaml(new FlowCall(location(), "flow", opts));
        assertResult("serializer/flowCallStepOutExpr.yml", result);
    }

    @Test
    public void testFormCall() throws Exception {
        FormField f = FormField.builder()
                .name("field-name")
                .label("field-label")
                .type("field-type")
                .cardinality(com.walmartlabs.concord.forms.FormField.Cardinality.ANY)
                .defaultValue("default-value")
                .allowedValue("allowed-value")
                .putOptions("o1", "v1")
                .location(location())
                .build();

        FormCallOptions opts = FormCallOptions.builder()
                .isYield(true)
                .saveSubmittedBy(true)
                .putRunAs("user", "u1")
                .putValues("k", "v")
                .addFields(f)
                .fieldsExpression("${fieldExpression}")
                .meta(meta())
                .build();

        String result = toYaml(new FormCall(location(), "form", opts));
        assertResult("serializer/formCallStep.yml", result);
    }

    @Test
    public void testGroupOfSteps() throws Exception {
        GroupOfStepsOptions opts = GroupOfStepsOptions.builder()
                .addOut("out")
                .errorSteps(steps())
                .withItems(withItems())
                .meta(meta())
                .build();

        String result = toYaml(new GroupOfSteps(location(), steps(), opts));
        assertResult("serializer/groupOfSteps.yml", result);
    }

    @Test
    public void testIfStep() throws Exception {
        List<Step> thenSteps = Collections.singletonList(new Expression(location(), "${exprForThen}", ExpressionOptions.builder().build()));
        List<Step> elseSteps = steps();
        String result = toYaml(new IfStep(location(), "${ifExpression}", thenSteps, elseSteps, simpleOptions()));
        assertResult("serializer/ifStep.yml", result);
    }

    @Test
    public void testParallelBlock() throws Exception {
        ParallelBlockOptions opts = ParallelBlockOptions.builder()
                .addOut("out")
                .meta(meta())
                .build();

        List<Step> steps = new ArrayList<>();
        steps.add(new Checkpoint(location(), "ch1", simpleOptions()));
        steps.add(new Checkpoint(location(), "ch2", simpleOptions()));

        String result = toYaml(new ParallelBlock(location(), steps, opts));
        assertResult("serializer/parallelBlock.yml", result);
    }

    @Test
    public void testParallelBlockOutExpr() throws Exception {
        ParallelBlockOptions opts = ParallelBlockOptions.builder()
                .putOutExpr("out", "${expr}")
                .meta(meta())
                .build();

        List<Step> steps = new ArrayList<>();
        steps.add(new Checkpoint(location(), "ch1", simpleOptions()));
        steps.add(new Checkpoint(location(), "ch2", simpleOptions()));

        String result = toYaml(new ParallelBlock(location(), steps, opts));
        assertResult("serializer/parallelBlockOutExpr.yml", result);
    }

    @Test
    public void testReturnStep() throws Exception {
        String result = toYaml(new ReturnStep(location()));
        assertResult("serializer/returnStep.yml", result);
    }

    @Test
    public void testScriptCall() throws Exception {
        ScriptCallOptions opts = ScriptCallOptions.builder()
                .body("print(\"Hello, \", myVar)")
                .putInput("in", "v")
                .withItems(withItems())
                .loop(serialLoop())
                .retry(retry())
                .errorSteps(steps())
                .meta(meta())
                .build();

        String result = toYaml(new ScriptCall(location(), "js", opts));
        assertResult("serializer/scriptStep.yml", result);
    }

    @Test
    public void testSetVariablesStep() throws Exception {
        String result = toYaml(new SetVariablesStep(location(), Collections.singletonMap("k1", "v1"), simpleOptions()));
        assertResult("serializer/setVariablesStep.yml", result);
    }

    @Test
    public void testSuspendStep() throws Exception {
        String result = toYaml(new SuspendStep(location(), "event", simpleOptions()));
        assertResult("serializer/suspendStep.yml", result);
    }

    @Test
    public void testSwitchStep() throws Exception {
        List<Map.Entry<String, List<Step>>> caseSteps = Collections.singletonList(new AbstractMap.SimpleEntry<>("red", steps()));
        List<Step> defaultSteps = Collections.singletonList(new Expression(location(), "defaultStep", ExpressionOptions.builder().build()));

        String result = toYaml(new SwitchStep(location(), "${switch}", caseSteps, defaultSteps, simpleOptions()));
        assertResult("serializer/switchStep.yml", result);
    }

    @Test
    public void testTaskCall() throws Exception {
        TaskCallOptions opts = TaskCallOptions.builder()
                .putInput("msg", "BOO")
                .out("out")
                .withItems(withItems())
                .loop(serialLoop())
                .retry(retry())
                .errorSteps(steps())
                .meta(meta())
                .build();

        String result = toYaml(new TaskCall(location(), "log", opts));
        assertResult("serializer/taskCallStep.yml", result);
    }

    @Test
    public void testTaskCallOutExpr() throws Exception {
        TaskCallOptions opts = TaskCallOptions.builder()
                .putInput("msg", "BOO")
                .putOutExpr("result", "${result}")
                .putOutExpr("v1", "${result.v1}")
                .withItems(withItems())
                .retry(retry())
                .errorSteps(steps())
                .meta(meta())
                .build();

        String result = toYaml(new TaskCall(location(), "log", opts));
        assertResult("serializer/taskCallStepOutExpr.yml", result);
    }

    @Test
    public void testTaskCallParallelWithItems() throws Exception {
        TaskCallOptions opts = TaskCallOptions.builder()
                .putInput("msg", "BOO")
                .out("out")
                .withItems(parallelWithItems())
                .retry(retry())
                .errorSteps(steps())
                .meta(meta())
                .build();

        String result = toYaml(new TaskCall(location(), "log", opts));
        assertResult("serializer/taskCallStepParallel.yml", result);
    }

    @Test
    public void testExprCallOutExpr() throws Exception {
        ExpressionOptions opts = ExpressionOptions.builder()
                .putOutExpr("out-result", "${result.first}")
                .meta(meta())
                .errorSteps(steps())
                .build();

        String result = toYaml(new Expression(location(), "${a}", opts));
        assertResult("serializer/expressionStepOutExpr.yml", result);
    }

    @Test
    public void testProcessDefinition() throws Exception {
        Map<String, Form> forms = Collections.singletonMap("form1", Form.builder()
                .name("form1")
                .addFields(FormField.builder()
                        .name("field1")
                        .type("string")
                        .cardinality(com.walmartlabs.concord.forms.FormField.Cardinality.ANY)
                        .location(location())
                        .build())
                .location(location())
                .build());

        Trigger trigger = Trigger.builder()
                .name("github")
                .location(location())
                .putConfiguration("entryPoint", "www")
                .putConfiguration("useInitiator", true)
                .putConditions("type", "push")
                .putConditions("status", Arrays.asList("opened", "reopened"))
                .putArguments("arg", "arg-value")
                .addActiveProfiles("p1")
                .build();

        Imports imports = Imports.of(Collections.singletonList(Import.MvnDefinition.builder()
                .url("http://url")
                .dest("dest")
                .build()));

        ProcessDefinitionConfiguration cfg = ProcessDefinitionConfiguration.builder()
                .debug(true)
                .parallelLoopParallelism(123)
                .build();

        ProcessDefinition pd = ProcessDefinition.builder()
                .configuration(cfg)
                .forms(forms)
                .putFlows("flow1", Flow.of(Location.builder().build(), steps()))
                .addPublicFlows("flow1")
                .addTriggers(trigger)
                .imports(imports)
                .build();

        String result = toYaml(pd);
        assertResult("serializer/processDefinition.yml", result);
    }

    private void assertResult(String resource, String result) throws Exception {
        URI uri = ClassLoader.getSystemResource(resource).toURI();
        String expected = new String(Files.readAllBytes(Paths.get(uri)));
        assertEquals(expected, result);
    }

    private static Location location() {
        return Location.builder()
                .fileName("test.concord.yml")
                .build();
    }

    private static SimpleOptions simpleOptions() {
        return SimpleOptions.of(meta());
    }

    private static Map<String, Serializable> meta() {
        Map<String, Serializable> options = new LinkedHashMap<>();
        options.put("meta-1", "v1");
        options.put("meta-2", "v2");
        return options;
    }

    private static WithItems withItems() {
        ArrayList<String> items = new ArrayList<>();
        items.add("item1");
        items.add("item2");
        return WithItems.of(items, WithItems.Mode.SERIAL);
    }

    private static WithItems parallelWithItems() {
        ArrayList<String> items = new ArrayList<>();
        items.add("item1");
        items.add("item2");
        return WithItems.of(items, WithItems.Mode.PARALLEL);
    }

    private static Loop serialLoop() {
        ArrayList<String> items = new ArrayList<>();
        items.add("item1");
        items.add("item2");
        return Loop.builder()
                .items(items)
                .mode(Loop.Mode.SERIAL)
                .build();
    }

    private static Retry retry() {
        return Retry.builder()
                .times(1)
                .delay(Duration.ofDays(1))
                .timesExpression("${times}")
                .delayExpression("${delay}")
                .input(Collections.singletonMap("retry-input", "v1"))
                .build();
    }

    private static List<Step> steps() {
        return Collections.singletonList(new Checkpoint(location(), "chp1", simpleOptions()));
    }

    private String toYaml(Object o) throws Exception {
        return serializer.getObjectMapper().writeValueAsString(o);
    }
}
