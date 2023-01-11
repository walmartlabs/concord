package com.walmartlabs.concord.server.org.project;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableKvEntry.class)
@JsonDeserialize(as = ImmutableKvEntry.class)
public interface KvEntry extends Serializable {

    long serialVersionUID = 1L;

    String key();

    Object value();

    @Nullable
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    OffsetDateTime lastUpdatedAt();

    static ImmutableKvEntry.Builder builder() {
        return ImmutableKvEntry.builder();
    }
}
