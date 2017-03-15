package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.agent.api.AgentResource;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.agent.api.PingResponse;
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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Named
public class AgentResourceImpl implements AgentResource, Resource {

    private final ExecutionManager executionManager;
    private final LogManager logManager;

    @Inject
    public AgentResourceImpl(ExecutionManager executionManager, LogManager logManager) {
        this.executionManager = executionManager;
        this.logManager = logManager;
    }

    @Override
    @Validate
    public void start(String instanceId, JobType type, String entryPoint, InputStream in) throws Exception {
        executionManager.start(instanceId, type, entryPoint, in);
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

    @Override
    @Validate
    public Response streamLog(@PathParam("id") String id) {
        Path f = logManager.open(id);
        if (!Files.exists(f)) {
            return Response.status(Status.NOT_FOUND)
                    .entity("Instance: " + id + ": log file not found")
                    .build();
        }

        StreamingOutput stream = out -> {
            byte[] ab = new byte[1024];

            try (InputStream in = Files.newInputStream(f, StandardOpenOption.READ)) {
                while (true) {
                    int read = in.read(ab, 0, ab.length);
                    if (read > 0) {
                        out.write(ab, 0, read);
                        out.flush();
                    }

                    if (read < ab.length) {
                        if (executionManager.getStatus(id) != JobStatus.RUNNING) {
                            // the log and the job are finished
                            break;
                        }

                        // job is still running, wait for more data
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        };

        return Response.ok(stream).build();
    }

    @Override
    public PingResponse ping() {
        return new PingResponse(true);
    }
}
