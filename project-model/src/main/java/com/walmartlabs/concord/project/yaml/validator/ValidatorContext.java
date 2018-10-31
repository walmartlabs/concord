package com.walmartlabs.concord.project.yaml.validator;

import com.fasterxml.jackson.core.JsonLocation;

import java.util.HashMap;
import java.util.Map;

public class ValidatorContext {
    private final Map<String, Map<String, JsonLocation>> counters = new HashMap<>();

    public void assertUnique(String counterName, String key, JsonLocation location) {
        Map<String, JsonLocation> keys = counters.computeIfAbsent(counterName, k -> new HashMap<>());
        JsonLocation old = keys.put(key, location);
        if (old != null) {
            throw new IllegalArgumentException(counterName + " '" + key + "' @:" + location + " already defined at @:" + old);
        }
    }
}