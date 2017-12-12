package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.api.process.ProcessKind;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class InitialQueueEntryProcessor implements PayloadProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public InitialQueueEntryProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        ProcessKind kind = payload.getHeader(Payload.PROCESS_KIND, ProcessKind.DEFAULT);
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);
        String initiator = payload.getHeader(Payload.INITIATOR);

        queueDao.insertInitial(instanceId, kind, parentInstanceId, projectId, initiator);

        return chain.process(payload.putHeader(Payload.HAS_QUEUE_RECORD, true));
    }
}
