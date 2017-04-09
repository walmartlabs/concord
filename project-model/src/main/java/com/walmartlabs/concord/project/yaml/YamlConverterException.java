package com.walmartlabs.concord.project.yaml;

import com.fasterxml.jackson.core.JsonProcessingException;

public class YamlConverterException extends JsonProcessingException {

    public YamlConverterException(String message) {
        super(message);
    }
}
