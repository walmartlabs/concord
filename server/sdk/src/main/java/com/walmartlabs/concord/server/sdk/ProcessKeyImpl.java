package com.walmartlabs.concord.server.sdk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An implementation of {@link ProcessKey} to simplify the deserialization
 * of {@link com.walmartlabs.concord.server.sdk.events.ProcessEvent} and
 * other classes.
 */
public class ProcessKeyImpl implements ProcessKey {

    private final UUID instanceId;
    private final OffsetDateTime createdAt;

    @JsonCreator
    public ProcessKeyImpl(@JsonProperty("instanceId") UUID instanceId,
                          @JsonProperty("createdAt") OffsetDateTime createdAt) {
        this.instanceId = instanceId;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getInstanceId() {
        return instanceId;
    }

    @Override
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
