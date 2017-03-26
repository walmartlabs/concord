package com.walmartlabs.concord.server.agent;

import com.walmartlabs.concord.agent.pool.AgentPool;
import com.walmartlabs.concord.server.cfg.AgentConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.net.URI;
import java.util.Arrays;

@Named
@Singleton
public class AgentPoolProvider implements Provider<AgentPool> {

    private final AgentConfiguration cfg;

    @Inject
    public AgentPoolProvider(AgentConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public AgentPool get() {
        // TODO support for multiple agents
        URI uri = cfg.getUri();

        // a hacky way to allow up to two simultaneous connections
        // TODO this should be replaced with "sticky" agents
        return new AgentPool(Arrays.asList(uri, uri));
    }
}
