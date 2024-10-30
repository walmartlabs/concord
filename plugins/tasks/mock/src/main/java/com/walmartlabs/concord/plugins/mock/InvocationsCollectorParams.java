package com.walmartlabs.concord.plugins.mock;

import com.walmartlabs.concord.sdk.MapUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

public class InvocationsCollectorParams implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, Object> params;

    public InvocationsCollectorParams(Map<String, Object> params) {
        this.params = params;
    }

    public boolean enabled() {
        return MapUtils.getBoolean(params, "enabled", true);
    }
}
