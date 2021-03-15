package com.walmartlabs.concord.agent.logging;

import javax.inject.Inject;
import java.util.Set;
import java.util.UUID;

public class CombinedLogAppender implements LogAppender {

    private final Set<LogAppender> appenders;

    @Inject
    public CombinedLogAppender(Set<LogAppender> appenders) {
        this.appenders = appenders;
    }

    @Override
    public void appendLog(UUID instanceId, byte[] ab) {
        appenders.forEach(a -> a.appendLog(instanceId, ab));
    }

    @Override
    public boolean appendLog(UUID instanceId, long segmentId, byte[] ab) {
        boolean result = true;
        for (LogAppender a : appenders) {
            boolean done = a.appendLog(instanceId, segmentId, ab);
            result = result && done;
        }
        return result;
    }

    @Override
    public boolean updateSegment(UUID instanceId, long segmentId, LogSegmentStats stats) {
        boolean result = true;
        for (LogAppender a : appenders) {
            boolean done = a.updateSegment(instanceId, segmentId, stats);
            result = result && done;
        }
        return result;
    }
}
