package com.walmartlabs.concord.project.yaml.converter;

import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlScript;
import io.takari.bpm.model.ScriptTask;

public class YamlScriptConverter implements StepConverter<YamlScript> {

    @Override
    public Chunk convert(ConverterContext ctx, YamlScript s) throws YamlConverterException {
        Chunk c = new Chunk();

        String id = ctx.nextId();
        switch (s.getType()) {
            case CONTENT: {
                c.addElement(new ScriptTask(id, s.getType(), s.getLanguage(), s.getBody(), true));
                break;
            }
            case REFERENCE: {
                c.addElement(new ScriptTask(id, s.getType(), null, s.getBody(), true));
                break;
            }
            default:
                throw new YamlConverterException("Unsupported script task type: " + s.getType());
        }
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Script"));

        return c;
    }
}
