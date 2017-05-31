package com.walmartlabs.concord.server.rpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.cfg.LogStoreConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipOutputStream;

@Named
public class JobQueueImpl extends TJobQueueGrpc.TJobQueueImplBase {

    private static final Logger log = LoggerFactory.getLogger(JobQueueImpl.class);
    private static final String[] IGNORED_FILES = {"\\.git"};
    private static final int PAYLOAD_CHUNK_SIZE = 512 * 1024; // 512kb

    private final ProcessQueueDao queueDao;
    private final LogStoreConfiguration logStoreCfg;
    private final PayloadManager payloadManager;

    @Inject
    public JobQueueImpl(ProcessQueueDao queueDao, LogStoreConfiguration logStoreCfg, PayloadManager payloadManager) {
        this.queueDao = queueDao;
        this.logStoreCfg = logStoreCfg;
        this.payloadManager = payloadManager;
    }

    @Override
    public void poll(TJobRequest request, StreamObserver<TJobResponse> responseObserver) {
        ProcessEntry entry = queueDao.poll();
        if (entry == null) {
            responseObserver.onCompleted();
            return;
        }

        String instanceId = entry.getInstanceId();
        try {
            Path src = getPayload(instanceId);
            try (InputStream in = Files.newInputStream(src)) {
                int read;
                byte[] ab = new byte[PAYLOAD_CHUNK_SIZE];

                while ((read = in.read(ab)) > 0) {
                    TJobResponse r = TJobResponse.newBuilder()
                            .setInstanceId(instanceId)
                            .setType(TJobType.RUNNER)
                            .setChunk(ByteString.copyFrom(ab, 0, read))
                            .build();

                    responseObserver.onNext(r);
                }

                responseObserver.onCompleted();
            }
        } catch (IOException e) {
            responseObserver.onError(e);
            return;
        }
    }

    private Path getPayload(String instanceId) throws IOException {
        Path src = payloadManager.getWorkspace(instanceId);
        if (src == null) {
            log.warn("getPayload ['{}'] -> not found", instanceId);
            throw new IllegalArgumentException("Process not found: " + instanceId);
        }

        // TODO cfg
        Path tmp = Files.createTempFile("payload", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
            IOUtils.zip(zip, src, IGNORED_FILES);
        }
        return tmp;
    }

    @Override
    @WithTimer
    public void updateStatus(TJobStatusUpdate request, StreamObserver<Empty> responseObserver) {
        String agentId = request.getAgentId();
        String instanceId = request.getInstanceId();
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
        // TODO move into a logmanager or something

        String instanceId = request.getInstanceId();
        byte[] data = request.getData().toByteArray();
        // TODO validate the id

        Path p = logStoreCfg.getBaseDir().resolve(instanceId + ".log");
        // TODO cache open files?
        try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.APPEND)) {
            out.write(data);
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
        // TODO cfg
        Path tmp;
        try {
            tmp = Files.createTempFile("attachments", ".zip");
            Files.write(tmp, request.getData().toByteArray());
        } catch (IOException e) {
            responseObserver.onError(e);
            return;
        }

        String instanceId = request.getInstanceId();
        try {
            payloadManager.updateState(instanceId, tmp);
        } catch (IOException e) {
            responseObserver.onError(e);
            return;
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();

        log.info("uploadAttachments ['{}'] -> done", instanceId);
    }

    private boolean isSuspended(String instanceId) {
        String name = Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/" +
                Constants.Files.JOB_STATE_DIR_NAME + "/" +
                Constants.Files.SUSPEND_MARKER_FILE_NAME;

        return payloadManager.getResource(instanceId, name) != null;
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
