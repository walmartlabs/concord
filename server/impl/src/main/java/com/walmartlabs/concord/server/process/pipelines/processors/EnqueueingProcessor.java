package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Set;
import java.util.UUID;

@Named
public class EnqueueingProcessor implements PayloadProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public EnqueueingProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        Set<String> tags = payload.getHeader(Payload.PROCESS_TAGS);

        ProcessEntry e = queueDao.get(instanceId);
        if (e == null) {
            throw new ProcessException(instanceId, "Process not found: " + instanceId);
        }

        ProcessStatus s = e.getStatus();
        if (s != ProcessStatus.PREPARING && s != ProcessStatus.RESUMING && s != ProcessStatus.SUSPENDED) {
            throw new ProcessException(instanceId, "Invalid process status: " + s);
        }

        queueDao.update(instanceId, ProcessStatus.ENQUEUED, tags);
        return chain.process(payload);
    }
}
