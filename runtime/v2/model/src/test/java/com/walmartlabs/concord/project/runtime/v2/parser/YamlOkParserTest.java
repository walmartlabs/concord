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
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.parser.StepOptions;
import org.junit.Test;

import java.io.Serializable;
import java.util.*;

import static org.junit.Assert.*;

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

        // input
        Map<String, Object> input = new HashMap<>();
        input.put("k", "v");
        input.put("k2", 2);
        input.put("k3", false);
        assertEquals(input, t.getOptions().input());

        // retry
        assertNotNull(t.getOptions().retry());
        assertEquals(1, t.getOptions().retry().times());
        assertEquals(2, t.getOptions().retry().delay());
        Map<String, Object> retryInput = new HashMap<>();
        retryInput.put("k", "retry-1");
        retryInput.put("k2", "retry-2");
        retryInput.put("k3", Collections.singletonMap("kk", "vv"));
        assertEquals(retryInput, t.getOptions().retry().input());

        // meta
        assertMeta(t.getOptions());
    }

    // Short Task Definition Test
    @Test
    public void test001() throws Exception {
        ProcessDefinition pd = load("001.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof TaskCall);
        TaskCall t = (TaskCall) main.get(0);
        assertEquals("myShortTask", t.getName());

        // arg
        assertNotNull(t.getOptions().input());
        assertEquals(Collections.singletonMap("0", (Serializable)"boo"), t.getOptions().input());

        // meta
        assertMeta(t.getOptions());
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
        assertEquals("result", t.getOptions().out());

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
        assertEquals(2, t.getOptions().retry().delay());
        Map<String, Object> retryInput = new HashMap<>();
        retryInput.put("k", "retry-1");
        retryInput.put("k2", "retry-2");
        retryInput.put("k3", Collections.singletonMap("kk", "vv"));
        assertEquals(retryInput, t.getOptions().retry().input());

        // meta
        assertMeta(t.getOptions());
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
        assertNull(errorStep.getOptions());

        // meta
        assertMeta(t.getOptions());
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
        assertNull(errorStep.getOptions());

        // withItems
        assertEquals("a", ((List<String>)t.getOptions().withItems().value()).get(0));

        // meta
        assertMeta(t.getOptions());
    }

    // Forms Definition Test
    @Test
    public void test006() throws Exception {
        ProcessDefinition pd = load("006.yml");

        List<Step> main = pd.flows().get("main");

        assertEquals(1, main.size());

        assertTrue(main.get(0) instanceof FormCall);
        FormCall t = (FormCall) main.get(0);

        assertNotNull(t.getName());
        assertNotNull(t.getLocation());
        assertEquals("myForm", t.getName());
        assertTrue(t.getOptions().yield());
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

        assertNotNull(pd.forms());
        assertEquals(1, pd.forms().size());
        Form fd = pd.forms().items().get(0);
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

        assertEquals(3, imports.items().size());

        Import i = imports.items().get(0);
        assertEquals("git", i.type());
        Import.GitDefinition g = (Import.GitDefinition)i;
        assertEquals("https://github.com/me/my_private_repo.git", g.url());
        assertEquals("test", g.name());
        assertEquals("1.2.3", g.version());
        assertEquals("/", g.path());
        assertEquals("/dest", g.dest());
        assertEquals(Arrays.asList("a", "b"), g.exclude());
        assertEquals(Import.SecretDefinition.builder().name("my_secret_key").build(), g.secret());

        assertEquals("git", imports.items().get(1).type());
        assertEquals("mvn", imports.items().get(2).type());
    }

    // Configuration Definition Test
    @Test
    public void test008() throws Exception {
        ProcessDefinition pd = load("008.yml");

        ProcessConfiguration cfg = pd.configuration();
        assertNotNull(cfg);

        assertEquals("main-test", cfg.entryPoint());
        assertEquals(Arrays.asList("d1", "d2"), cfg.dependencies());
        assertEquals(Collections.singletonMap("k", "v"), cfg.arguments());
    }

    @Test
    public void test009() throws Exception {
        ProcessDefinition pd = load("009.yml");

        List<Trigger> triggers = pd.triggers();
        assertNotNull(triggers);

        assertEquals(6, triggers.size());

        Trigger t = triggers.get(0);
        assertEquals("github", t.name());

        t = triggers.get(1);
        assertEquals("github", t.name());

        t = triggers.get(2);
        assertEquals("cron", t.name());

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
        assertEquals("groovy", t.getName());

        // withItems
        assertEquals(1, t.getOptions().withItems().value());

        // input
        Map<String, Object> input = new HashMap<>();
        input.put("k", "v1");
        assertEquals(input, t.getOptions().input());

        // retry
        assertNotNull(t.getOptions().retry());
        assertEquals(1, t.getOptions().retry().times());
        assertEquals(2, t.getOptions().retry().delay());
        Map<String, Object> retryInput = new HashMap<>();
        retryInput.put("k", "v");
        assertEquals(retryInput, t.getOptions().retry().input());

        // meta
        assertMeta(t.getOptions());
    }

    private static void assertMeta(StepOptions o) {
        assertNotNull(o.meta());
        assertEquals(Collections.singletonMap("m1", (Serializable)"v1"), o.meta());
    }
}
