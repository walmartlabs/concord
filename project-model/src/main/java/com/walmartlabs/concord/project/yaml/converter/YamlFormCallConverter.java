package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlFormCall;
import io.takari.bpm.model.UserTask;
import io.takari.bpm.model.form.FormExtension;

import java.util.Map;

public class YamlFormCallConverter implements StepConverter<YamlFormCall> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlFormCall s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();
        Map<String, Object> opts = s.getOptions();
        if (opts != null && opts.isEmpty()) {
            opts = null;
        }
        c.addElement(new UserTask(id, new FormExtension(s.getKey(), opts)));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Form: " + s.getKey()));

        return c;
    }
}
