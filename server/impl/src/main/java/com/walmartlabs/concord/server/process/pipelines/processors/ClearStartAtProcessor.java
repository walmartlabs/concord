package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;

/**
 * Removes the "startAt" parameter from the process configuration and
 * the process queue entry. Necessary when resuming a process - it doesn't make
 * any sense to re-use the same "startAt" value which might be in the past
 * already.
 */
@Named
public class ClearStartAtProcessor implements PayloadProcessor {

    private final ProcessQueueDao queueDao;

    @Inject
    public ClearStartAtProcessor(ProcessQueueDao queueDao) {
        this.queueDao = queueDao;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        payload = clearConfiguration(payload);
        queueDao.clearStartAt(payload.getProcessKey());
        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private static Payload clearConfiguration(Payload payload) {
        Map<String, Object> cfg = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (cfg == null) {
            return payload;
        }

        Map<String, Object> m = new HashMap<>(cfg);
        m.remove(Constants.Request.START_AT_KEY);
        return payload.putHeader(Payload.REQUEST_DATA_MAP, m);
    }
}
