package com.walmartlabs.concord.plugins.nexus.perf2;

import com.walmartlabs.concord.agent.pool.AgentPool;

import java.net.URI;
import java.util.Collection;

public interface AgentPoolFactory {

    AgentPool create(Collection<URI> hosts);
}
