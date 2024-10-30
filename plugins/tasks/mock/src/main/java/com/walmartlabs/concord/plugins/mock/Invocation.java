package com.walmartlabs.concord.plugins.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.common.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(as = ImmutableInvocation.class)
@JsonDeserialize(as = ImmutableInvocation.class)
public interface Invocation {

    @Nullable
    String fileName();

    int line();

    String taskName();

    String methodName();

    @AllowNulls
    @Value.Default
    default List<Object> args() {
        return List.of();
    }

    static ImmutableInvocation.Builder builder() {
        return ImmutableInvocation.builder();
    }
}
