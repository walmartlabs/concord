package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlDockerStep;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;

import java.util.Arrays;

public class YamlDockerStepConverter implements StepConverter<YamlDockerStep> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlDockerStep s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();
        ELCall call = createELCall("docker", Arrays.asList(s.getImage(), s.getCmd(), s.getEnv(), "${" + Constants.Context.LOCAL_PATH_KEY + "}"));
        c.addElement(new ServiceTask(id, ExpressionType.SIMPLE, call.getExpression(), call.getArgs(), null));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Docker: " + s.getImage()));

        return c;
    }
}
