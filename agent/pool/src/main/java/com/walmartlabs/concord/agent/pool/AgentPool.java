package com.walmartlabs.concord.agent.pool;

import com.walmartlabs.concord.agent.api.JobResource;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class AgentPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentPool.class);

    private final Collection<URI> hosts;
    private final Client client = ClientBuilder.newClient();
    private final GenericObjectPool<JobResource> pool;

    public AgentPool(Collection<URI> hosts) {
        this.hosts = new HashSet<>(hosts);
        this.pool = new GenericObjectPool<>(new AgentFactory());
    }

    // TODO less generic exception
    public JobResource acquire(long timeout) throws Exception {
        return pool.borrowObject(timeout);
    }

    public void release(JobResource r) {
        pool.returnObject(r);
    }

    @Override
    public void close() {
        pool.close();
        client.close();
    }

    private JobResource connect(URI host) throws InterruptedException {
        JobResource proxy;

        // TODO constants
        for (int attempt = 0; attempt < 3; attempt++) {
            ResteasyWebTarget t = (ResteasyWebTarget) client.target(host);
            proxy = t.proxy(JobResource.class);

            try {
                if (proxy.count() > 0) {
                    log.info("Skipping busy agent: {}", host);
                    return null;
                }
                return proxy;
            } catch (Exception e) {
                log.warn("Error while trying to acquire an agent {}: {}", host, e.getMessage());
                // TODO constants
                Thread.sleep(3000);
            }
        }

        return null;
    }

    private final class AgentFactory implements PooledObjectFactory<JobResource> {

        @Override
        public PooledObject<JobResource> makeObject() throws Exception {
            synchronized (hosts) {
                Queue<URI> available = new LinkedList<>(hosts);
                while (true) {
                    URI host = available.poll();
                    if (host == null) {
                        // TODO better exceptions?
                        throw new Exception("No available hosts left");
                    }

                    JobResource proxy = connect(host);
                    if (proxy == null) {
                        continue;
                    }

                    hosts.remove(host);
                    return new DefaultPooledObject<>(proxy);
                }
            }
        }

        @Override
        public void destroyObject(PooledObject<JobResource> p) throws Exception {
            JobResource r = p.getObject();
            try {
                r.cancelAll();
            } catch (Exception e) {
                log.warn("Error while resetting an agent: {}", r, e);
            }
        }

        @Override
        public boolean validateObject(PooledObject<JobResource> p) {
            // TODO reset an agent?
            return true;
        }

        @Override
        public void activateObject(PooledObject<JobResource> p) throws Exception {
        }

        @Override
        public void passivateObject(PooledObject<JobResource> p) throws Exception {
        }
    }
}
