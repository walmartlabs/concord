package com.walmartlabs.concord.server.agent;

import com.walmartlabs.concord.agent.pool.AgentPool;
import com.walmartlabs.concord.server.cfg.AgentConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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

        // a hacky way to allow multiple simultaneous connections
        List<URI> l = new ArrayList<>(cfg.getMaxConn());
        for (int i = 0; i < cfg.getMaxConn(); i++) {
            l.add(uri);
        }

        return new AgentPool(l);
    }
}
