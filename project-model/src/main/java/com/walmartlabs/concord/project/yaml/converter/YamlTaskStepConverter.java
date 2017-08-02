package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlTaskStep;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;
import io.takari.bpm.model.VariableMapping;

import java.util.Set;

public class YamlTaskStepConverter implements StepConverter<YamlTaskStep> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlTaskStep s) throws YamlConverterException {
        Chunk c = new Chunk();

        Set<VariableMapping> inVars = getVarMap(s.getOptions(), "in");
        Set<VariableMapping> outVars = getVarMap(s.getOptions(), "out");

        String id = ctx.nextId();
        String expr = "${" + s.getKey() + "}";
        c.addElement(new ServiceTask(id, ExpressionType.DELEGATE, expr, inVars, outVars, true));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Task: " + s.getKey()));

        applyErrorBlock(ctx, c, id, s.getOptions());

        return c;
    }

}
