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
    public static final String AGENT_MAX_CONNECTIONS_KEY = "AGENT_MAX_CONN";

    private final URI uri;
    private final int maxConn;

    public AgentConfiguration() {
        String s = Utils.getEnv(AGENT_URL_KEY, "http://localhost:8002");
        this.uri = URI.create(s);

        s = Utils.getEnv(AGENT_MAX_CONNECTIONS_KEY, "5");
        this.maxConn = Integer.parseInt(s);
        log.info("init -> uri: {}, maxConn: {}", uri, maxConn);
    }

    public URI getUri() {
        return uri;
    }

    public int getMaxConn() {
        return maxConn;
    }
}
