package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlCall;
import io.takari.bpm.model.CallActivity;
import io.takari.bpm.model.VariableMapping;

import java.util.Set;

public class YamlCallConverter implements StepConverter<YamlCall> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlCall s) throws YamlConverterException {
        Chunk c = new Chunk();

        Set<VariableMapping> inVars = getVarMap(s.getOptions(), "in");
        Set<VariableMapping> outVars = getVarMap(s.getOptions(), "out");

        String id = ctx.nextId();
        c.addElement(new CallActivity(id, s.getKey(), inVars, outVars, true));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Flow call: " + s.getKey()));

        applyErrorBlock(ctx, c, id, s.getOptions());

        return c;
    }
}
