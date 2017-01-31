package com.walmartlabs.concord.plugins.nexus.perf;

import com.sonatype.nexus.perftest.controller.AgentPool;
import com.sonatype.nexus.perftest.controller.JMXServiceURLs;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Named
@Singleton
public class AgentPoolHolder {

    private final Map<String, AgentPoolManager> agents = new HashMap<>();

    /**
     * Creates a new JMX agent pool for specified URLs.
     *
     * @param id   Unique ID that will be associated with the created pool.
     * @param urls
     */
    public void connect(String id, String[] urls) {
        synchronized (agents) {
            AgentPoolManager m = agents.get(id);
            if (m != null) {
                throw new IllegalStateException("The agent pool for '" + id + "' is already created");
            }

            m = new AgentPoolManager(new AgentPool(JMXServiceURLs.of(urls)));
            agents.put(id, m);
        }
    }

    public AgentPoolManager get(String id) {
        synchronized (agents) {
            return agents.get(id);
        }
    }

    public void close(String id) {
        AgentPoolManager m;

        synchronized (agents) {
            m = agents.remove(id);
        }

        if (m == null) {
            return;
        }

        m.release();
    }
}
