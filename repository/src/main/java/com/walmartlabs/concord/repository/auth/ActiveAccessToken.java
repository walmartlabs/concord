package com.walmartlabs.concord.repository.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonDeserialize(as = ImmutableActiveAccessToken.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface ActiveAccessToken {

    String token();

    @Nullable
    @JsonProperty("expires_at")
    OffsetDateTime expiresAt();

}