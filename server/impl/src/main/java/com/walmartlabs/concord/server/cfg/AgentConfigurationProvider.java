package com.walmartlabs.concord.server.cfg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.net.URI;

@Named
@Singleton
public class AgentConfigurationProvider implements Provider<AgentConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigurationProvider.class);

    public static final String AGENT_URL_KEY = "AGENT_URL";

    @Override
    public AgentConfiguration get() {
        String s = Utils.getEnv(AGENT_URL_KEY, "http://localhost:8002");
        log.info("Agent's URL: {}", s);
        return new AgentConfiguration(URI.create(s));
    }
}
