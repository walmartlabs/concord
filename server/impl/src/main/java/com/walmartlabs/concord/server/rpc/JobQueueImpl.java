package com.walmartlabs.concord.server.rpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;
import static com.walmartlabs.concord.server.process.state.ProcessStateManager.zipTo;

@Named
public class JobQueueImpl extends TJobQueueGrpc.TJobQueueImplBase {

    private static final Logger log = LoggerFactory.getLogger(JobQueueImpl.class);
    private static final int PAYLOAD_CHUNK_SIZE = 512 * 1024; // 512kb

    private final ProcessQueueDao queueDao;
    private final ProcessStateManager stateManager;
    private final LogManager logManager;

    @Inject
    public JobQueueImpl(ProcessQueueDao queueDao, ProcessStateManager stateManager, LogManager logManager) {
        this.queueDao = queueDao;
        this.stateManager = stateManager;
        this.logManager = logManager;
    }

    @Override
    public void poll(TJobRequest request, StreamObserver<TJobResponse> responseObserver) {
        ProcessEntry entry = queueDao.poll();
        if (entry == null) {
            responseObserver.onCompleted();
            return;
        }

        UUID instanceId = entry.getInstanceId();

        try {
            // TODO this probably can be replaced with an in-memory buffer
            Path tmp = Files.createTempFile("payload", ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
                stateManager.export(instanceId, zipTo(zip));
            }

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

        if (status == ProcessStatus.FINISHED && isSuspended(instanceId)) {
            status = ProcessStatus.SUSPENDED;
        }

        queueDao.update(instanceId, agentId, status);

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();

        log.info("updateStatus ['{}', '{}', {}] -> done", agentId, instanceId, status);
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
            Path tmpDir = Files.createTempDirectory("attachments");
            byte[] data = request.getData().toByteArray();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
                IOUtils.unzip(zip, tmpDir);
            }

            stateManager.transaction(tx -> {
                stateManager.delete(tx, instanceId, path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME, Constants.Files.JOB_STATE_DIR_NAME));
                stateManager.importPath(tx, instanceId, Constants.Files.JOB_ATTACHMENTS_DIR_NAME, tmpDir);
            });
        } catch (IOException e) {
            responseObserver.onError(e);
            return;
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();

        log.info("uploadAttachments ['{}'] -> done", instanceId);
    }

    private boolean isSuspended(UUID instanceId) {
        String resource = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
                Constants.Files.JOB_STATE_DIR_NAME,
                Constants.Files.SUSPEND_MARKER_FILE_NAME);

        return stateManager.exists(instanceId, resource);
    }

    private static ProcessStatus convert(TJobStatus s) {
        switch (s) {
            case RUNNING:
                return ProcessStatus.RUNNING;
            case COMPLETED:
                return ProcessStatus.FINISHED;
            case FAILED:
            case CANCELLED:
                return ProcessStatus.FAILED;
            default:
                throw new IllegalArgumentException("Unsupported job status type: " + s);
        }
    }
}
