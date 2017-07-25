package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class FailProcessor implements ExceptionProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public FailProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    @WithTimer
    public void process(Payload payload, Exception e) {
        UUID instanceId = payload.getInstanceId();
        queueDao.update(instanceId, ProcessStatus.FAILED);
    }
}
