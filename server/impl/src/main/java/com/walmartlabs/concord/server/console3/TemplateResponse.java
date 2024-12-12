package com.walmartlabs.concord.server.console3;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public record TemplateResponse(String template, Map<String, Object> extraVars) {

    public TemplateResponse(String template) {
        this(requireNonNull(template), Map.of());
    }
}
