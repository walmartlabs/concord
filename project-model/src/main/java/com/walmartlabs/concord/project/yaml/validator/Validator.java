package com.walmartlabs.concord.project.yaml.validator;

import com.walmartlabs.concord.project.yaml.model.YamlCheckpoint;
import com.walmartlabs.concord.project.yaml.model.YamlProcessDefinition;
import com.walmartlabs.concord.project.yaml.model.YamlProject;
import com.walmartlabs.concord.project.yaml.model.YamlStep;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Validator {

    private static final Map<Class, StepValidator> validators = new HashMap<>();

    static {
        validators.put(YamlCheckpoint.class, new YamlCheckpointValidator());
    }

    public static void validate(ValidatorContext ctx, YamlProcessDefinition def) {
        if (def.getSteps() == null) {
            return;
        }
        validate(ctx, def.getSteps().toList());
    }

    public static void validate(ValidatorContext ctx, YamlProject prj) {
        if (prj.getFlows() == null) {
            return;
        }

        prj.getFlows().forEach((s, yamlSteps) -> validate(ctx, yamlSteps));
    }

    private static void validate(ValidatorContext ctx, List<YamlStep> steps) {
        if (steps == null) {
            return;
        }
        steps.forEach(s -> validate(ctx, s));
    }

    @SuppressWarnings("unchecked")
    private static void validate(ValidatorContext ctx, YamlStep s) {
        StepValidator v = validators.get(s.getClass());
        if (v != null) {
            v.validate(ctx, s);
        }
    }
}