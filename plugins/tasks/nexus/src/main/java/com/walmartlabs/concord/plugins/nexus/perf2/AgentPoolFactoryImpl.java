package com.walmartlabs.concord.plugins.nexus.perf2;

import com.walmartlabs.concord.agent.pool.AgentPool;

import javax.inject.Named;
import java.net.URI;
import java.util.Collection;

@Named
public class AgentPoolFactoryImpl implements AgentPoolFactory {

    @Override
    public AgentPool create(Collection<URI> hosts) {
        return new AgentPool(hosts);
    }
}
