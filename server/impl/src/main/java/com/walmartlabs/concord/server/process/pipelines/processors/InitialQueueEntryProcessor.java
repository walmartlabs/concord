package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
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
        String projectName = payload.getHeader(Payload.PROJECT_NAME);
        String initiator = payload.getHeader(Payload.INITIATOR);
        queueDao.insertInitial(instanceId, projectName, initiator);
        return chain.process(payload);
    }
}
