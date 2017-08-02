package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlEvent;
import io.takari.bpm.model.IntermediateCatchEvent;

public class YamlEventConverter implements StepConverter<YamlEvent> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlEvent s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();
        c.addElement(new IntermediateCatchEvent(id, s.getName()));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Event: " + s.getName()));

        return c;
    }
}
