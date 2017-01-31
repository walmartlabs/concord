package com.walmartlabs.concord.plugins.templates.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.junit.Test;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.SimpleBindings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TemplateTest {

    @Test
    public void test() throws Exception {
        ScriptEngine se = new NashornScriptEngineFactory().getScriptEngine("--no-java");

        Map<String, Object> m = new HashMap<>();
        m.put("ansible", Collections.singletonMap("playbook", "hello.yml"));

        Bindings b = new SimpleBindings();
        b.put("_input", m);

        Object o = se.eval("load('classpath:_main.js')", b);

        ObjectMapper om = new ObjectMapper();
        om.writeValue(System.out, o);
    }
}