package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.agent.api.JobResource;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;

@Named
public class JobResourceImpl implements JobResource, Resource {

    private final ExecutionManager executionManager;

    @Inject
    public JobResourceImpl(ExecutionManager executionManager) {
        this.executionManager = executionManager;
    }

    @Override
    @Validate
    public String start(InputStream in, JobType type, String entryPoint) throws Exception {
        return executionManager.start(in, type, entryPoint);
    }

    @Override
    @Validate public JobStatus getStatus(String id) {
        return executionManager.getStatus(id);
    }

    @Override
    public void cancelAll() {
        executionManager.cancel();
    }

    @Override
    @Validate
    public void cancel(String id, boolean waitToFinish) {
        executionManager.cancel(id, waitToFinish);
    }

    @Override
    public int count() {
        return executionManager.jobCount();
    }
}
