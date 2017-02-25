package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.cfg.AttachmentStoreConfiguration;
import com.walmartlabs.concord.server.history.ProcessHistoryDao;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessExecutorCallback;
import com.walmartlabs.concord.server.process.ProcessExecutorImpl;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Sends a payload to an agent.
 */
@Named
public class AgentProcessor implements PayloadProcessor {

    private final ProcessExecutorImpl executor;
    private final ProcessHistoryDao historyDao;
    private final Executor threadPool;
    private final AttachmentStoreConfiguration attachmentCfg;

    @Inject
    public AgentProcessor(ProcessExecutorImpl executor, ProcessHistoryDao historyDao, AttachmentStoreConfiguration attachmentCfg) {
        this.executor = executor;
        this.historyDao = historyDao;
        this.attachmentCfg = attachmentCfg;

        // TODO cfg
        this.threadPool = Executors.newCachedThreadPool();
    }

    @Override
    public Payload process(Payload payload) {
        // synchronously add an initial record to the process history
        historyDao.insertInitial(payload.getInstanceId(),
                payload.getHeader(Payload.INITIATOR),
                payload.getHeader(LogFileProcessor.LOG_FILE_NAME));

        threadPool.execute(() -> {
            Path attachmentFile = attachmentCfg.getBaseDir()
                    .resolve(payload.getInstanceId() + ".zip");

            ProcessExecutorCallback callback = new HistoryCallback(historyDao);
            executor.run(payload.getInstanceId(),
                    payload.getHeader(ArchivingProcessor.ARCHIVE_FILE),
                    payload.getHeader(DependenciesProcessor.ENTRY_POINT_NAME),
                    payload.getHeader(LogFileProcessor.LOG_FILE_PATH),
                    attachmentFile,
                    callback);
        });

        return payload.removeHeader(ArchivingProcessor.ARCHIVE_FILE);
    }

    private static final class HistoryCallback implements ProcessExecutorCallback {

        private final ProcessHistoryDao historyDao;

        private HistoryCallback(ProcessHistoryDao historyDao) {
            this.historyDao = historyDao;
        }

        @Override
        public void onStatusChange(String instanceId, ProcessStatus status) {
            historyDao.update(instanceId, status);
        }

        @Override
        public void onUpdate(String instanceId) {
            historyDao.touch(instanceId);
        }
    }
}
