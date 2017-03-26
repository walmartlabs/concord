package com.walmartlabs.concord.agent.pool;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

public class AgentPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentPool.class);

    private static final int MAX_RECONNECTION_ATTEMPTS = 3;
    private static final int RECONNECTION_DELAY = 3000;
    private static final long DEFAULT_ACQUIRE_TIMEOUT = 10000;

    private final Client client;
    private final Collection<URI> hosts;
    private final GenericObjectPool<AgentConnection> pool;

    public AgentPool(Collection<URI> hosts) {
        HttpClient hc = HttpClientBuilder.create()
                .setConnectionManager(new PoolingHttpClientConnectionManager())
                .build();

        this.client = new ResteasyClientBuilder()
                .httpEngine(new ApacheHttpClient4Engine(hc))
                .build();

        this.hosts = new ArrayList<>(hosts);
        this.pool = new GenericObjectPool<>(new AgentFactory());
    }

    public AgentConnection getConnection() {
        return getConnection(DEFAULT_ACQUIRE_TIMEOUT);
    }

    public AgentConnection getConnection(long timeout) {
        try {
            return pool.borrowObject(timeout);
        } catch (Exception e) {
            throw new AgentPoolException("Agent connection error", e);
        }
    }

    void release(AgentConnection c) {
        pool.returnObject(c);
    }

    @Override
    public void close() {
        pool.close();
        client.close();
    }

    private AgentConnection connect(URI host) throws InterruptedException {
        AgentConnection proxy;

        for (int attempt = 0; attempt < MAX_RECONNECTION_ATTEMPTS; attempt++) {
            try {
                WebTarget target = client.target(host);

                proxy = new AgentConnection(this, target);
                proxy.ping();

                return proxy;
            } catch (Exception e) {
                log.warn("Error while trying to acquire an agent {}: {}", host, e.getMessage());
                Thread.sleep(RECONNECTION_DELAY);
            }
        }

        return null;
    }

    private final class AgentFactory implements PooledObjectFactory<AgentConnection> {

        @Override
        public PooledObject<AgentConnection> makeObject() throws Exception {
            synchronized (hosts) {
                Queue<URI> available = new LinkedList<>(hosts);
                while (true) {
                    URI host = available.poll();
                    if (host == null) {
                        throw new Exception("No available agents");
                    }

                    AgentConnection proxy = connect(host);
                    if (proxy == null) {
                        continue;
                    }

                    hosts.remove(host);
                    return new DefaultPooledObject<>(proxy);
                }
            }
        }

        @Override
        public void destroyObject(PooledObject<AgentConnection> p) throws Exception {
            AgentConnection r = p.getObject();
            try {
                r.cancelAll();
            } catch (Exception e) {
                log.warn("Error while resetting an agent: {}", r, e);
            }
        }

        @Override
        public boolean validateObject(PooledObject<AgentConnection> p) {
            return true;
        }

        @Override
        public void activateObject(PooledObject<AgentConnection> p) throws Exception {
        }

        @Override
        public void passivateObject(PooledObject<AgentConnection> p) throws Exception {
        }
    }
}
