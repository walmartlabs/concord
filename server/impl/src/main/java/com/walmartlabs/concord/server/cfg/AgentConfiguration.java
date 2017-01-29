package com.walmartlabs.concord.server.cfg;

import java.io.Serializable;
import java.net.URI;

public class AgentConfiguration implements Serializable {

    private final URI uri;

    public AgentConfiguration(URI uri) {
        this.uri = uri;
    }

    public URI getUri() {
        return uri;
    }
}
