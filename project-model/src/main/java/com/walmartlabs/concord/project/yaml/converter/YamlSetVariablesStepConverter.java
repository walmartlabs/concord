package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlSetVariablesStep;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;
import io.takari.bpm.model.VariableMapping;

import java.util.Collections;
import java.util.Set;

public class YamlSetVariablesStepConverter implements StepConverter<YamlSetVariablesStep> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlSetVariablesStep s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();
        String expression = "${vars.set(execution, __0)}";

        Object vars = StepConverter.deepConvert(s.getVariables());
        Set<VariableMapping> inVars = Collections.singleton(new VariableMapping(null, null, vars, "__0", true));

        c.addElement(new ServiceTask(id, ExpressionType.SIMPLE, expression, inVars, null));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Set variables"));

        return c;
    }
}
