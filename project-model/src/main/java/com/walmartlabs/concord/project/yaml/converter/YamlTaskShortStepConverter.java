package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlTaskShortStep;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;

public class YamlTaskShortStepConverter implements StepConverter<YamlTaskShortStep> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlTaskShortStep s) throws YamlConverterException {
        Chunk c = new Chunk();

        ELCall call = createELCall(s.getKey(), s.getArg());

        String id = ctx.nextId();
        c.addElement(new ServiceTask(id, ExpressionType.SIMPLE, call.getExpression(), call.getArgs(), null, true));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Task: " + s.getKey()));

        return c;
    }
}
