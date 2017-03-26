package com.walmartlabs.concord.server.cfg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;
import java.net.URI;

@Named
@Singleton
public class AgentConfiguration implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    public static final String AGENT_URL_KEY = "AGENT_URL";

    private final URI uri;

    public AgentConfiguration() {
        String s = Utils.getEnv(AGENT_URL_KEY, "http://localhost:8002");
        this.uri = URI.create(s);
        log.info("init -> uri: {}", uri);
    }

    public AgentConfiguration(URI uri) {
        this.uri = uri;
    }

    public URI getUri() {
        return uri;
    }
}
