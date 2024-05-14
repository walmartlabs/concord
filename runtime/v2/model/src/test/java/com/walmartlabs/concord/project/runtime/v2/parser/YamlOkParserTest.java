package com.walmartlabs.concord.project.runtime.v2.parser;

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

import com.walmartlabs.concord.forms.FormField.Cardinality;
import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.runtime.v2.Constants;
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.parser.StepOptions;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class YamlOkParserTest extends AbstractParserTest {

    // Full Task Definition Test
    @Test
    public void test000() throws Exception {
        ProcessDefinition pd = load("000.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof TaskCall);
        TaskCall t = (TaskCall) main.get(0);
        assertEquals("boo", t.getName());

        // options
        assertNotNull(t.getOptions());
        assertEquals("result", t.getOptions().out());

        // withItems
        assertEquals(1, t.getOptions().withItems().value());

        // loop
        assertEquals(1, t.getOptions().loop().items());
        assertEquals(Loop.Mode.SERIAL, t.getOptions().loop().mode());

        // input
        Map<String, Object> input = new HashMap<>();
        input.put("k", "v");
        input.put("k2", 2);
        input.put("k3", false);
        assertEquals(input, t.getOptions().input());

        // retry
        assertNotNull(t.getOptions().retry());
        assertEquals(1, t.getOptions().retry().times());
        assertEquals(Duration.ofSeconds(2), t.getOptions().retry().delay());
        Map<String, Object> retryInput = new HashMap<>();
        retryInput.put("k", "retry-1");
        retryInput.put("k2", "retry-2");
        retryInput.put("k3", Collections.singletonMap("kk", "vv"));
        assertEquals(retryInput, t.getOptions().retry().input());

        // meta
        assertMeta("Boo", t.getOptions());
    }

    @Test
    public void test000_1() throws Exception {
        ProcessDefinition pd = load("000.1.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof TaskCall);
        TaskCall t = (TaskCall) main.get(0);
        assertEquals("boo", t.getName());

        // options
        assertNotNull(t.getOptions());

        // input
        assertEquals(0, t.getOptions().input().size());
        assertEquals("${inExpr}", t.getOptions().inputExpression());
    }

    @Test
    public void test000_2() throws Exception {
        ProcessDefinition pd = load("000.2.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof TaskCall);
        TaskCall t = (TaskCall) main.get(0);
        assertEquals("project", t.getName());

        // options
        assertNotNull(t.getOptions());

        // name
        assertEquals("Test name", t.getOptions().meta().get(Constants.SEGMENT_NAME));

        // input
        assertNotNull(t.getOptions().input());

        assertEquals("ProjectName", t.getOptions().input().get("name"));
        assertEquals("Default", t.getOptions().input().get("org"));
        assertEquals("create", t.getOptions().input().get("action"));
    }

    // Full Call Flow Definition Test
    @Test
    public void test002() throws Exception {
        ProcessDefinition pd = load("002.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof FlowCall);
        FlowCall t = (FlowCall) main.get(0);
        assertEquals("boo", t.getFlowName());

        // options
        assertNotNull(t.getOptions());
        assertEquals(Collections.singletonList("result"), t.getOptions().out());

        // withItems
        assertEquals(1, t.getOptions().withItems().value());

        // input
        Map<String, Object> input = new HashMap<>();
        input.put("k", "v");
        input.put("k2", 2);
        input.put("k3", false);
        assertEquals(input, t.getOptions().input());

        // retry
        assertNotNull(t.getOptions().retry());
        assertEquals(1, t.getOptions().retry().times());
        assertEquals(Duration.ofSeconds(2), t.getOptions().retry().delay());
        Map<String, Object> retryInput = new HashMap<>();
        retryInput.put("k", "retry-1");
        retryInput.put("k2", "retry-2");
        retryInput.put("k3", Collections.singletonMap("kk", "vv"));
        assertEquals(retryInput, t.getOptions().retry().input());

        // meta
        assertMeta("boo-call", t.getOptions());
    }

    @Test
    public void test002_1() throws Exception {
        ProcessDefinition pd = load("002.1.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof FlowCall);
        FlowCall t = (FlowCall) main.get(0);
        assertEquals("boo", t.getFlowName());

        // options
        assertNotNull(t.getOptions());
        // input
        assertEquals(0, t.getOptions().input().size());
        assertEquals("${inExpr}", t.getOptions().inputExpression());
    }

    // Snapshot Definition Test
    @Test
    public void test003() throws Exception {
        ProcessDefinition pd = load("003.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof Checkpoint);
        Checkpoint t = (Checkpoint) main.get(0);
        assertEquals("ZZZ", t.getName());

        // meta
        assertMeta(t.getOptions());
    }

    // Full Expression Definition Test
    @Test
    public void test004() throws Exception {
        ProcessDefinition pd = load("004.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof Expression);
        Expression t = (Expression) main.get(0);
        assertEquals("${boo}", t.getExpr());

        // options
        assertNotNull(t.getOptions());
        assertEquals("result", t.getOptions().out());

        // error
        assertNotNull(t.getOptions().errorSteps());
        assertEquals(1, t.getOptions().errorSteps().size());
        assertTrue(t.getOptions().errorSteps().get(0) instanceof Expression);
        Expression errorStep = (Expression) t.getOptions().errorSteps().get(0);
        assertEquals("${booError}", errorStep.getExpr());
        assertNotNull(errorStep.getOptions());

        // meta
        assertMeta("expression-call", t.getOptions());
    }

    // Group of Steps Definition Test
    @Test
    @SuppressWarnings("unchecked")
    public void test005() throws Exception {
        ProcessDefinition pd = load("005.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof GroupOfSteps);
        GroupOfSteps t = (GroupOfSteps) main.get(0);

        // error
        assertNotNull(t.getOptions().errorSteps());
        assertEquals(1, t.getOptions().errorSteps().size());
        assertTrue(t.getOptions().errorSteps().get(0) instanceof Expression);
        Expression errorStep = (Expression) t.getOptions().errorSteps().get(0);
        assertEquals("${exp}", errorStep.getExpr());
        assertNotNull(errorStep.getOptions());

        // withItems
        assertEquals("a", ((List<String>) t.getOptions().withItems().value()).get(0));

        // meta
        assertMeta("Test try", t.getOptions());
    }

    // Forms Definition Test
    @Test
    public void test006() throws Exception {
        ProcessDefinition pd = load("006.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(2, main.size());

        assertTrue(main.get(0) instanceof FormCall);
        FormCall t = (FormCall) main.get(0);

        assertNotNull(t.getName());
        assertNotNull(t.getLocation());
        assertEquals("myForm", t.getName());
        assertNotNull(t.getOptions());
        assertTrue(t.getOptions().isYield());
        assertTrue(t.getOptions().saveSubmittedBy());
        assertEquals(Collections.singletonMap("username", Arrays.asList("userA", "userB")), t.getOptions().runAs());
        assertEquals(Collections.singletonMap("myField", "a different value"), t.getOptions().values());
        assertEquals(2, t.getOptions().fields().size());
        FormField field = t.getOptions().fields().get(0);
        assertEquals("firstName", field.name());
        assertEquals("string", field.type());
        assertEquals(Cardinality.ONE_AND_ONLY_ONE, field.cardinality());
        assertEquals(0, field.options().size());
        assertNotNull(field.location());


        assertTrue(main.get(1) instanceof FormCall);
        FormCall t2 = (FormCall) main.get(1);

        assertNotNull(t2.getOptions());
        assertNotNull(t2.getOptions().valuesExpression());
        assertFalse(t2.getOptions().isYield());
        assertFalse(t2.getOptions().saveSubmittedBy());
        assertEquals("${{ 'fieldA': 'valueA' }}", t2.getOptions().valuesExpression());
        assertEquals("${{ 'username': [ 'userA', 'userB' ] }}", t2.getOptions().runAsExpression());


        assertNotNull(pd.forms());
        assertEquals(1, pd.forms().size());
        Form fd = pd.forms().get("myForm");
        assertNotNull(fd);
        assertEquals("myForm", fd.name());
        assertNotNull(fd.location());
        assertNotNull(fd.fields());
        assertEquals(2, fd.fields().size());
    }

    // Imports Definition Test
    @Test
    public void test007() throws Exception {
        ProcessDefinition pd = load("007.yml");

        Imports imports = pd.imports();
        assertNotNull(imports);

        assertEquals(4, imports.items().size());

        Import i = imports.items().get(0);
        assertEquals("git", i.type());
        Import.GitDefinition g = (Import.GitDefinition) i;
        assertEquals("https://github.com/me/my_private_repo.git", g.url());
        assertEquals("test", g.name());
        assertEquals("1.2.3", g.version());
        assertEquals("/", g.path());
        assertEquals("/dest", g.dest());
        assertEquals(Arrays.asList("a", "b"), g.exclude());
        assertEquals(Import.SecretDefinition.builder().name("my_secret_key").build(), g.secret());

        assertEquals("git", imports.items().get(1).type());
        assertEquals("mvn", imports.items().get(2).type());
        assertEquals("dir", imports.items().get(3).type());
    }

    // Configuration Definition Test
    @Test
    public void test008() throws Exception {
        ProcessDefinition pd = load("008.yml");

        ProcessDefinitionConfiguration cfg = pd.configuration();
        assertNotNull(cfg);

        assertTrue(cfg.debug());
        assertEquals("main-test", cfg.entryPoint());
        assertEquals(Arrays.asList("d1", "d2"), cfg.dependencies());
        assertEquals(Collections.singletonMap("k", "v"), cfg.arguments());
        assertEquals(Collections.singletonMap("k", "v1"), cfg.requirements());
        assertEquals(Duration.parse("PT1H"), cfg.processTimeout());
        assertEquals(ExclusiveMode.of("X", ExclusiveMode.Mode.cancel), cfg.exclusive());
        assertEquals(EventConfiguration.builder()
                .recordTaskInVars(true)
                .inVarsBlacklist(Collections.singletonList("pass"))
                .recordTaskOutVars(true)
                .outVarsBlacklist(Collections.singletonList("bass"))
                .recordTaskMeta(true)
                .metaBlacklist(Collections.singletonList("bass"))
                .build(), cfg.events());
    }

    /**
     * Triggers
     */
    @Test
    public void test009() throws Exception {
        ProcessDefinition pd = load("009.yml");

        List<Trigger> triggers = pd.triggers();
        assertNotNull(triggers);

        assertEquals(6, triggers.size());

        Trigger t = triggers.get(0);
        assertEquals("github", t.name());
        assertFalse((Boolean) t.configuration().get("ignoreEmptyPush"));

        t = triggers.get(1);
        assertEquals("github", t.name());

        t = triggers.get(2);
        assertEquals("cron", t.name());
        assertEquals(Collections.singletonMap("withSecret", "secret-name"), t.configuration().get("runAs"));

        t = triggers.get(3);
        assertEquals("manual", t.name());

        t = triggers.get(4);
        assertEquals("example", t.name());

        t = triggers.get(5);
        assertEquals("oneops", t.name());
    }

    // Full form of IF definition
    @Test
    public void test010() throws Exception {
        ProcessDefinition pd = load("010.yml");

        List<Step> main = pd.flows().get("default");
        assertEquals(1, main.size());

        IfStep ifStep = (IfStep) main.get(0);
        assertEquals("${myVar > 0}", ifStep.getExpression());
        assertEquals(2, ifStep.getThenSteps().size());
        assertEquals(1, ifStep.getElseSteps().size());
    }

    // Switch full definition
    @Test
    public void test011() throws Exception {
        ProcessDefinition pd = load("012.yml");

        List<Step> main = pd.flows().get("default");
        assertEquals(1, main.size());

        SwitchStep switchStep = (SwitchStep) main.get(0);
        assertEquals("${myVar}", switchStep.getExpression());
        assertEquals(2, switchStep.getCaseSteps().size());
        assertEquals("red", switchStep.getCaseSteps().get(0).getKey());
        assertEquals("green", switchStep.getCaseSteps().get(1).getKey());
        assertEquals(1, switchStep.getDefaultSteps().size());
    }

    // publicFlows definition
    @Test
    public void test013() throws Exception {
        ProcessDefinition pd = load("013.yml");

        Set<String> publicFlows = pd.publicFlows();
        Set<String> flowNames = pd.flows().keySet();

        assertEquals(1, publicFlows.size());
        assertTrue(publicFlows.contains("publicFlow"));
        assertTrue(flowNames.contains(publicFlows.iterator().next()));
    }

    // script definition
    @Test
    public void test014() throws Exception {
        ProcessDefinition pd = load("014.yml");

        List<Step> main = pd.flows().get("default");
        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof ScriptCall);
        ScriptCall t = (ScriptCall) main.get(0);
        assertEquals("groovy", t.getLanguageOrRef());

        // withItems
        assertEquals(1, t.getOptions().withItems().value());

        // input
        Map<String, Object> input = new HashMap<>();
        input.put("k", "v1");
        assertEquals(input, t.getOptions().input());

        // out
        assertEquals("result", t.getOptions().out());

        // retry
        assertNotNull(t.getOptions().retry());
        assertEquals(1, t.getOptions().retry().times());
        assertEquals(Duration.ofSeconds(2), t.getOptions().retry().delay());
        Map<String, Object> retryInput = new HashMap<>();
        retryInput.put("k", "v");
        assertEquals(retryInput, t.getOptions().retry().input());

        // meta
        assertMeta(t.getOptions());
    }


    // script definition
    @Test
    public void test014_1() throws Exception {
        ProcessDefinition pd = load("014.1.yml");

        List<Step> main = pd.flows().get("default");
        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof ScriptCall);
        ScriptCall t = (ScriptCall) main.get(0);
        assertEquals("groovy", t.getLanguageOrRef());

        // input
        assertEquals(0, t.getOptions().input().size());
        assertEquals("${inExpr}", t.getOptions().inputExpression());
    }

    // resources definition
    @Test
    public void test015() throws Exception {
        ProcessDefinition pd = load("015.yml");

        Resources resources = pd.resources();
        assertEquals(3, resources.concord().size());

        assertEquals("glob:abc", resources.concord().get(0));
        assertEquals("regex:boo", resources.concord().get(1));
        assertEquals("concord/myfile.yml", resources.concord().get(2));
    }

    // set variables definition
    @Test
    public void test016() throws Exception {
        ProcessDefinition pd = load("016.yml");

        List<Step> main = pd.flows().get("default");
        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof SetVariablesStep);
        SetVariablesStep t = (SetVariablesStep) main.get(0);
        assertMeta(t.getOptions());

        Map<String, Serializable> m = new HashMap<>();
        m.put("k", "v");
        m.put("x", 2);
        assertEquals(m, t.getVars());
    }

    // exit step
    @Test
    public void test017() throws Exception {
        ProcessDefinition pd = load("017.yml");

        List<Step> main = pd.flows().get("default");
        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof ExitStep);
        ExitStep t = (ExitStep) main.get(0);
        assertNotNull(t.getLocation());
    }

    // return step
    @Test
    public void test018() throws Exception {
        ProcessDefinition pd = load("018.yml");

        List<Step> main = pd.flows().get("default");
        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof ReturnStep);
        ReturnStep t = (ReturnStep) main.get(0);
        assertNotNull(t.getLocation());
    }

    // Profiles
    @Test
    public void test019() throws Exception {
        ProcessDefinition pd = load("019.yml");

        assertTrue(pd.flows().isEmpty());
        assertEquals(1, pd.profiles().size());

        Profile p1 = pd.profiles().get("p1");
        assertNotNull(p1);

        // flows
        assertEquals(1, p1.flows().size());
        assertTrue(p1.flows().get("default").get(0) instanceof ReturnStep);

        assertEquals(1, p1.forms().size());
        assertNotNull(p1.forms().get("myForm"));
        assertEquals(2, p1.forms().get("myForm").fields().size());

        assertTrue(p1.configuration().debug());
    }

    @Test
    public void testArgsOrder() throws Exception {
        ProcessDefinition pd = load("args-order.concord.yml");
        Map.Entry<String, Object> e = pd.configuration().arguments().entrySet().iterator().next();
        assertEquals("a", e.getKey());
    }

    private static void assertMeta(StepOptions o) {
        assertMeta(null, o);
    }

    private static void assertMeta(String stepName, StepOptions o) {
        assertNotNull(o.meta());
        Map<String, Serializable> expected = new HashMap<>();
        if (stepName != null) {
            expected.put("segmentName", stepName);
        }
        expected.put("m1", "v1");
        assertEquals(expected, o.meta());
    }
}
