package com.walmartlabs.concord.project.yaml;

import com.walmartlabs.concord.project.yaml.converter.ConverterContext;
import com.walmartlabs.concord.project.yaml.model.YamlProcessDefinition;
import com.walmartlabs.concord.project.yaml.model.YamlStep;
import io.takari.bpm.model.ProcessDefinition;

import java.util.List;

public final class YamlProcessConverter {

    public static ProcessDefinition convert(String name, List<YamlStep> steps) throws YamlConverterException {
        return ConverterContext.convert(name, steps);
    }

    public static ProcessDefinition convert(YamlProcessDefinition def) throws YamlConverterException {
        return ConverterContext.convert(def.getName(), def.getSteps());
    }

    private YamlProcessConverter() {
    }
}
