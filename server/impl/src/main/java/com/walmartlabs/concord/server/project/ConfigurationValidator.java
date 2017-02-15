package com.walmartlabs.concord.server.project;

import java.util.Map;

public interface ConfigurationValidator {

    void validate(Map<String, Object> m);
}
