package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.agent.api.JobResource;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.common.IOUtils;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

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
    @Validate
    public JobStatus getStatus(String id) {
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

    @Override
    public Response downloadAttachments(@PathParam("id") String id) {
        java.nio.file.Path p;
        try {
            p = executionManager.zipAttachments(id);
        } catch (IOException e) {
            throw new WebApplicationException("Error while downloading job's attachments", e);
        }

        if (p == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok().entity((StreamingOutput) out -> {
            try (InputStream in = Files.newInputStream(p)) {
                IOUtils.copy(in, out);
            }
        }).build();
    }
}
