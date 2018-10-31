package com.walmartlabs.concord.project.yaml.validator;

import com.walmartlabs.concord.project.yaml.model.YamlStep;

public interface StepValidator<T extends YamlStep> {

    void validate(ValidatorContext ctx, T step);
}