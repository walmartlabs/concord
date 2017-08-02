package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlReturn;
import io.takari.bpm.model.EndEvent;

public class YamlReturnConverter implements StepConverter<YamlReturn> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlReturn s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();
        c.addElement(new EndEvent(id, s.getErrorCode()));
        c.addSourceMap(id, toSourceMap(s, "Return from a flow (code: " + s.getErrorCode() + ")"));

        // skip adding an output, it should be the last element of a branch

        return c;
    }
}
