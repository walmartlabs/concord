package com.walmartlabs.concord.server.api.org.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecretUpdateRequest implements Serializable {

    @ConcordKey
    private final String name;

    private final SecretVisibility visibility;

    public SecretUpdateRequest(SecretVisibility visibility) {
        this(null, visibility);
    }

    @JsonCreator
    public SecretUpdateRequest(@JsonProperty("name") String name,
                               @JsonProperty("visibility") SecretVisibility visibility) {

        this.name = name;
        this.visibility = visibility;
    }

    public String getName() {
        return name;
    }

    public SecretVisibility getVisibility() {
        return visibility;
    }

    @Override
    public String toString() {
        return "SecretUpdateRequest{" +
                "name='" + name + '\'' +
                ", visibility=" + visibility +
                '}';
    }
}
