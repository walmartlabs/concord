package com.walmartlabs.concord.agent.pool;

import com.walmartlabs.concord.agent.api.AgentResource;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.agent.api.PingResponse;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.InputStream;

public class AgentConnection implements AutoCloseable, AgentResource {

    private final AgentResource delegate;
    private final AgentPool pool;

    public AgentConnection(AgentPool pool, WebTarget target) {
        this.pool = pool;
        this.delegate = ((ResteasyWebTarget) target).proxy(AgentResource.class);
    }

    @Override
    public void start(String instanceId, JobType type, String entryPoint, InputStream in) throws Exception {
        delegate.start(instanceId, type, entryPoint, in);
    }

    @Override
    public JobStatus getStatus(String id) {
        return delegate.getStatus(id);
    }

    @Override
    public void cancelAll() {
        delegate.cancelAll();
    }

    @Override
    public void cancel(String id, boolean waitToFinish) {
        delegate.cancel(id, waitToFinish);
    }

    @Override
    public int count() {
        return delegate.count();
    }

    @Override
    public Response downloadAttachments(String id) {
        return delegate.downloadAttachments(id);
    }

    @Override
    public Response streamLog(String id) {
        return delegate.streamLog(id);
    }

    @Override
    public PingResponse ping() {
        return delegate.ping();
    }

    @Override
    public void close() {
        pool.release(this);
    }
}
