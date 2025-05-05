package com.walmartlabs.concord.common;

import java.time.Instant;

public interface TimeProvider {

    Instant now();

    void sleep(long ms) throws InterruptedException;
}
