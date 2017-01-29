package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.LogResource;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.RandomAccessFile;

@Named
public class LogResourceImpl implements LogResource, Resource {

    private final ExecutionManager executionManager;
    private final LogManager logManager;

    @Inject
    public LogResourceImpl(ExecutionManager executionManager, LogManager logManager) {
        this.executionManager = executionManager;
        this.logManager = logManager;
    }

    @Override
    @Validate
    public Response stream(@PathParam("id") String id) {
        File f = logManager.open(id);
        if (!f.exists()) {
            return Response.status(Status.NOT_FOUND)
                    .entity("Instance: " + id + ": log file not found")
                    .build();
        }

        StreamingOutput stream = out -> {
            int pos = 0;
            byte[] ab = new byte[1024];

            while (true) {
                try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                    raf.seek(pos);

                    int read = raf.read(ab, 0, ab.length);
                    if (read > 0) {
                        pos += read;
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
}
