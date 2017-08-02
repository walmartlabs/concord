package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlExpressionStep;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;
import io.takari.bpm.model.VariableMapping;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class YamlExpressionStepConverter implements StepConverter<YamlExpressionStep> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlExpressionStep s) throws YamlConverterException {
        Chunk c = new Chunk();

        Set<VariableMapping> outVars = null;
        Map<String, Object> opts = s.getOptions();
        if (opts != null) {
            String out = (String) opts.get("out");
            if (out != null) {
                outVars = Collections.singleton(new VariableMapping(ServiceTask.EXPRESSION_RESULT_VAR, null, out));
            }
        }

        String id = ctx.nextId();
        c.addElement(new ServiceTask(id, ExpressionType.SIMPLE, s.getExpr(), null, outVars, true));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Expression: " + s.getExpr()));

        applyErrorBlock(ctx, c, id, s.getOptions());

        return c;
    }
}
