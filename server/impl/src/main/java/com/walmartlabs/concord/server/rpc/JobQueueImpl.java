package com.walmartlabs.concord.server.rpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.ProcessManager.PayloadEntry;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;

@Named
public class JobQueueImpl extends TJobQueueGrpc.TJobQueueImplBase {

    private static final Logger log = LoggerFactory.getLogger(JobQueueImpl.class);
    private static final int PAYLOAD_CHUNK_SIZE = 512 * 1024; // 512kb

    private final ProcessStateManager stateManager;
    private final LogManager logManager;
    private final ProcessManager processManager;

    @Inject
    public JobQueueImpl(ProcessStateManager stateManager, LogManager logManager, ProcessManager processManager) {
        this.stateManager = stateManager;
        this.logManager = logManager;
        this.processManager = processManager;
    }

    @Override
    public void poll(TJobRequest request, StreamObserver<TJobResponse> responseObserver) {
        try {
            PayloadEntry p = processManager.nextPayload();
            if (p == null) {
                responseObserver.onCompleted();
                return;
            }

            UUID instanceId = p.getProcessEntry().getInstanceId();

            Path tmp = p.getPayloadArchive();
            try (InputStream in = Files.newInputStream(tmp)) {
                int read;
                byte[] ab = new byte[PAYLOAD_CHUNK_SIZE];

                while ((read = in.read(ab)) > 0) {
                    TJobResponse r = TJobResponse.newBuilder()
                            .setInstanceId(instanceId.toString())
                            .setType(TJobType.RUNNER)
                            .setChunk(ByteString.copyFrom(ab, 0, read))
                            .build();

                    responseObserver.onNext(r);
                }

                responseObserver.onCompleted();
            }
        } catch (IOException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    @WithTimer
    public void updateStatus(TJobStatusUpdate request, StreamObserver<Empty> responseObserver) {
        String agentId = request.getAgentId();
        UUID instanceId = UUID.fromString(request.getInstanceId());
        ProcessStatus status = convert(request.getStatus());

        processManager.updateStatus(instanceId, agentId, status);

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    @WithTimer
    public void appendLog(TJobLogEntry request, StreamObserver<Empty> responseObserver) {
        // TODO validate the id
        String instanceId = request.getInstanceId();
        byte[] data = request.getData().toByteArray();

        try {
            logManager.log(UUID.fromString(instanceId), data);
        } catch (IOException e) {
            log.warn("appendLog ['{}'] -> error while writing to a log file: {}", instanceId, e.getMessage());
            responseObserver.onError(e);
            return;
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    @WithTimer
    public void uploadAttachments(TAttachments request, StreamObserver<Empty> responseObserver) {
        UUID instanceId = UUID.fromString(request.getInstanceId());
        try {
            // TODO cfg
            byte[] data = request.getData().toByteArray();

            Path tmpIn = Files.createTempFile("attachments", ".zip");
            Files.write(tmpIn, data);

            Path tmpDir = Files.createTempDirectory("attachments");
            IOUtils.unzip(tmpIn, tmpDir);

            stateManager.transaction(tx -> {
                stateManager.delete(tx, instanceId, path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME, InternalConstants.Files.JOB_STATE_DIR_NAME));
                stateManager.importPath(tx, instanceId, InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME, tmpDir);
            });
        } catch (IOException e) {
            responseObserver.onError(e);
            return;
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();

        log.info("uploadAttachments ['{}'] -> done", instanceId);
    }

    private static ProcessStatus convert(TJobStatus s) {
        switch (s) {
            case RUNNING:
                return ProcessStatus.RUNNING;
            case COMPLETED:
                return ProcessStatus.FINISHED;
            case FAILED:
                return ProcessStatus.FAILED;
            case CANCELLED:
                return ProcessStatus.CANCELLED;
            default:
                throw new IllegalArgumentException("Unsupported job status type: " + s);
        }
    }
}
