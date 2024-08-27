package com.walmartlabs.concord.cli.runner;

import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingClient;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class CliLoggingClient implements LoggingClient {

    private final AtomicLong counter = new AtomicLong(1);

    @Override
    public long createSegment(UUID correlationId, String name) {
        return counter.getAndIncrement();
    }
}
