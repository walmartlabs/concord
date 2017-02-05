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
    public Payload process(Payload payload) {
        ProcessExecutorCallback callback = new ProcessExecutorCallback() {
            @Override
            public void onStart(String instanceId) {
                String initiator = payload.getHeader(Payload.INITIATOR);
                String logFileName = payload.getHeader(LogFileProcessor.LOG_FILE_NAME);
                historyDao.insertInitial(instanceId, initiator, logFileName);
            }

            @Override
            public void onStatusChange(String instanceId, ProcessStatus status) {
                historyDao.update(instanceId, status);
            }

            @Override
            public void onUpdate(String instanceId) {
                historyDao.touch(instanceId);
            }
        };

        threadPool.execute(() -> {
            executor.run(payload.getInstanceId(),
                    payload.getHeader(ArchivingProcessor.ARCHIVE_FILE),
                    payload.getHeader(DependenciesProcessor.ENTRY_POINT_NAME),
                    payload.getHeader(LogFileProcessor.LOG_FILE_PATH),
                    callback);
        });

        return payload.removeHeader(ArchivingProcessor.ARCHIVE_FILE);
    }
}
