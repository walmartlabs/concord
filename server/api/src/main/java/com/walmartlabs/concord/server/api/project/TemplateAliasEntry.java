package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

public class TemplateAliasEntry implements Serializable {

    @NotNull
    @ConcordKey
    private final String alias;

    @NotNull
    @Size(max = 2048)
    private final String url;

    @JsonCreator
    public TemplateAliasEntry(@JsonProperty("alias") String alias,
                              @JsonProperty("url") String url) {
        this.alias = alias;
        this.url = url;
    }

    public String getAlias() {
        return alias;
    }

    public String getUrl() {
        return url;
    }
}
