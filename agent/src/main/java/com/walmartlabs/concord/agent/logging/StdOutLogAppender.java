package com.walmartlabs.concord.agent.logging;

import java.util.UUID;

public class StdOutLogAppender implements LogAppender {

    private static final String PREFIX = "RUNNER: ";

    @Override
    public void appendLog(UUID instanceId, byte[] ab) {
        System.out.print(PREFIX + new String(ab));
    }

    @Override
    public boolean appendLog(UUID instanceId, long segmentId, byte[] ab) {
        System.out.print(PREFIX + new String(ab));
        return true;
    }

    @Override
    public boolean updateSegment(UUID instanceId, long segmentId, LogSegmentStats stats) {
        return true;
    }
}
