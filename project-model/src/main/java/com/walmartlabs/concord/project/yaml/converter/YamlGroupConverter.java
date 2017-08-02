package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlGroup;
import io.takari.bpm.model.AbstractElement;
import io.takari.bpm.model.SubProcess;

import java.util.List;

public class YamlGroupConverter implements StepConverter<YamlGroup> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlGroup s) throws YamlConverterException {
        Chunk c = new Chunk();

        // create a subprocess
        Chunk sub = ctx.convert(s.getSteps());
        List<AbstractElement> l = ctx.wrapAsProcess(sub);

        // add the subprocess
        String id = ctx.nextId();
        c.addElement(new SubProcess(id, l));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Group of steps"));

        // keep the subprocess' source map
        c.addSourceMaps(sub.getSourceMap());

        applyErrorBlock(ctx, c, id, s.getOptions());

        return c;
    }
}
