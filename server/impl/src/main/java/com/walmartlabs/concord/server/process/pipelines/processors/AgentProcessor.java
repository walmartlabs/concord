package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.history.ProcessHistoryDao;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessExecutorCallback;
import com.walmartlabs.concord.server.process.ProcessExecutorImpl;

import javax.inject.Inject;
import javax.inject.Named;
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

    @Inject
    public AgentProcessor(ProcessExecutorImpl executor, ProcessHistoryDao historyDao) {
        this.executor = executor;
        this.historyDao = historyDao;

        // TODO cfg
        this.threadPool = Executors.newCachedThreadPool();
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        boolean resumeMode = payload.getHeader(Payload.RESUME_EVENT_NAME) != null;

        if (resumeMode) {
            historyDao.update(payload.getInstanceId(), ProcessStatus.RESUMING);
        } else {
            // synchronously add an initial record to the process history
            historyDao.insertInitial(payload.getInstanceId(),
                    payload.getHeader(Payload.INITIATOR),
                    payload.getHeader(LogFileProcessor.LOG_FILE_NAME));
        }

        // run the payload asynchronously
        threadPool.execute(() -> {
            ProcessExecutorCallback callback = new HistoryCallback(historyDao);
            executor.run(payload.getInstanceId(),
                    payload.getHeader(ArchivingProcessor.ARCHIVE_FILE),
                    payload.getHeader(LogFileProcessor.LOG_FILE_PATH),
                    callback);

            // continue the chain asynchronously
            chain.process(payload);
        });

        return payload;
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
